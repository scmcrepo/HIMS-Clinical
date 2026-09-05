package com.hms.application.grievance;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.grievance.ComplianceContactEntity;
import com.hms.infrastructure.persistence.grievance.ComplianceContactJpaRepository;
import com.hms.infrastructure.persistence.grievance.GrievanceEntity;
import com.hms.infrastructure.persistence.grievance.GrievanceEventEntity;
import com.hms.infrastructure.persistence.grievance.GrievanceEventJpaRepository;
import com.hms.infrastructure.persistence.grievance.GrievanceJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Grievance redressal — DPDP s. 8(9) and s. 13.
 *
 * <p>The Act requires an <em>effective</em> mechanism, not merely a published
 * address. That word is why this class tracks acknowledgement separately from
 * resolution and records every step: a complaint that sat unread for eighty days and
 * was answered on day eighty-nine met the deadline and did not work.
 *
 * <h2>Three clocks, not one</h2>
 *
 * <ul>
 *   <li><b>Acknowledgement</b> — internal, 3 days. The complainant should know
 *       they were heard long before they know the answer.</li>
 *   <li><b>Target</b> — internal, 30 days. So the statutory ceiling does not
 *       become the working norm.</li>
 *   <li><b>Due</b> — statutory, 90 days.</li>
 * </ul>
 *
 * <p>Confirm the 90-day period with counsel; it is the widely reported reading
 * of the Rules and is encoded as a default, not a certainty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrievanceService {

    private final GrievanceJpaRepository grievances;
    private final GrievanceEventJpaRepository events;
    private final ComplianceContactJpaRepository contacts;
    private final AuditorAware<UUID> auditorAware;
    private final MeterRegistry meterRegistry;

    private static final Duration STATUTORY_WINDOW = Duration.ofDays(90);
    private static final Duration INTERNAL_TARGET = Duration.ofDays(30);
    private static final Duration ACKNOWLEDGE_WINDOW = Duration.ofDays(3);

    private static final DateTimeFormatter REF_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    // ── Intake ────────────────────────────────────────────────────────────

    /**
     * Record a grievance.
     *
     * <p>Deliberately permissive about identification: {@code patientId} may be
     * null when the complainant cannot be matched to a record. Requiring a match
     * first would be a tidy way of never recording the inconvenient complaints,
     * and someone complaining that you hold data about them wrongly may well not
     * appear in your records the way you expect.
     *
     * <p>Never rejects a duplicate. Two complaints about the same thing are two
     * complaints, and silently merging them would lose one person's account.
     */
    @Transactional
    public GrievanceEntity raise(UUID patientId, String complainantContact, String category,
                                 String channel, String subject, String body) {

        if (patientId == null && (complainantContact == null || complainantContact.isBlank())) {
            throw new BusinessRuleViolationException(
                "A grievance needs either a patient or contact details — otherwise "
                + "there is no way to tell the complainant what was decided");
        }

        Instant now = Instant.now();
        GrievanceEntity g = new GrievanceEntity();
        g.setGrievanceRef(nextRef());
        g.setPatientId(patientId);
        g.setComplainantContact(complainantContact);
        g.setCategory(category);
        g.setChannel(channel);
        g.setSubject(truncate(subject, 200));
        g.setBody(body);
        g.setReceivedAt(now);
        g.setTargetAt(now.plus(INTERNAL_TARGET));
        g.setDueAt(now.plus(STATUTORY_WINDOW));
        g.setState("RECEIVED");

        GrievanceEntity saved = grievances.save(g);
        recordEvent(saved.getId(), "RECEIVED", null, false);

        meterRegistry.counter("hms_grievances_total",
                              "category", category, "channel", channel).increment();
        // Ref and category only. The complaint body is encrypted at rest and has
        // no business in a log line.
        log.info("event=grievance.raised ref={} category={} channel={} due_at={}",
                 saved.getGrievanceRef(), category, channel, saved.getDueAt());
        return saved;
    }

    /**
     * Tell the complainant we have it.
     *
     * <p>Its own step because being heard and being answered are different
     * things, and the gap between them is where a complainant decides whether to
     * go to the Board.
     */
    @Transactional
    public GrievanceEntity acknowledge(UUID grievanceId, String note) {
        GrievanceEntity g = load(grievanceId);
        if (g.getAcknowledgedAt() != null) {
            return g;   // idempotent
        }
        if (!g.isOpen()) {
            throw new BusinessRuleViolationException(
                "Cannot acknowledge a grievance that is already " + g.getState());
        }
        g.setAcknowledgedAt(Instant.now());
        if ("RECEIVED".equals(g.getState())) {
            g.setState("ACKNOWLEDGED");
        }
        recordEvent(grievanceId, "ACKNOWLEDGED", note, true);

        long hours = Duration.between(g.getReceivedAt(), Instant.now()).toHours();
        meterRegistry.counter("hms_grievance_acknowledgements_total").increment();
        log.info("event=grievance.acknowledged ref={} hours_since_receipt={}",
                 g.getGrievanceRef(), hours);
        return grievances.save(g);
    }

    @Transactional
    public GrievanceEntity assign(UUID grievanceId, UUID assignee) {
        GrievanceEntity g = load(grievanceId);
        g.setAssignedTo(assignee);
        if ("RECEIVED".equals(g.getState()) || "ACKNOWLEDGED".equals(g.getState())) {
            g.setState("IN_PROGRESS");
        }
        recordEvent(grievanceId, "ASSIGNED", null, false);
        return grievances.save(g);
    }

    /** A working note. Not communicated unless the caller says it was. */
    @Transactional
    public GrievanceEventEntity addNote(UUID grievanceId, String note, boolean communicated) {
        load(grievanceId);
        return recordEvent(grievanceId, "NOTE", note, communicated);
    }

    /**
     * Resolve, with the answer the complainant will be given.
     *
     * <p>The resolution text is mandatory and not defaulted. A resolution with no
     * text is a status change dressed up as an answer, and the person who
     * complained has no way to tell the difference from being ignored.
     */
    @Transactional
    public GrievanceEntity resolve(UUID grievanceId, String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new BusinessRuleViolationException(
                "A resolution must say what was decided — this is what the "
                + "complainant will be told");
        }
        GrievanceEntity g = load(grievanceId);
        if (!g.isOpen()) {
            throw new BusinessRuleViolationException("Already " + g.getState());
        }

        Instant now = Instant.now();
        g.setResolution(resolution);
        g.setResolvedAt(now);
        g.setState("RESOLVED");
        recordEvent(grievanceId, "RESOLVED", null, true);

        long days = Duration.between(g.getReceivedAt(), now).toDays();
        boolean withinTarget = now.isBefore(g.getTargetAt());
        boolean withinStatutory = now.isBefore(g.getDueAt());

        meterRegistry.counter("hms_grievance_resolutions_total",
                              "within_target", String.valueOf(withinTarget),
                              "within_statutory", String.valueOf(withinStatutory)).increment();
        // WARN rather than INFO when the statutory window was missed: this is
        // the line someone will search for later.
        if (withinStatutory) {
            log.info("event=grievance.resolved ref={} days={} within_target={}",
                     g.getGrievanceRef(), days, withinTarget);
        } else {
            log.warn("event=grievance.resolved.late ref={} days={}", g.getGrievanceRef(), days);
        }
        return grievances.save(g);
    }

    /**
     * The complainant went to the Data Protection Board.
     *
     * <p>Recorded rather than treated as a failure. They are entitled to at any
     * point, and a mechanism that treated escalation as an error would be
     * measuring the wrong thing. The rate is still the truest signal of whether
     * this works.
     */
    @Transactional
    public GrievanceEntity recordEscalation(UUID grievanceId, String boardReference) {
        GrievanceEntity g = load(grievanceId);
        g.setEscalatedToBoard(true);
        g.setBoardReference(truncate(boardReference, 80));
        recordEvent(grievanceId, "ESCALATED", null, false);

        meterRegistry.counter("hms_grievance_escalations_total").increment();
        log.warn("event=grievance.escalated_to_board ref={} board_ref={}",
                 g.getGrievanceRef(), boardReference);
        return grievances.save(g);
    }

    /** Link a grievance to the incident it turned out to be about. */
    @Transactional
    public GrievanceEntity linkIncident(UUID grievanceId, UUID incidentId) {
        GrievanceEntity g = load(grievanceId);
        g.setIncidentId(incidentId);
        recordEvent(grievanceId, "NOTE", "Linked to a security incident", false);
        // A complaint is often the first sign of a breach, so this link is
        // worth an explicit log line rather than a silent column write.
        log.warn("event=grievance.linked_to_incident ref={} incident_id={}",
                 g.getGrievanceRef(), incidentId);
        return grievances.save(g);
    }

    @Transactional
    public GrievanceEntity withdraw(UUID grievanceId, String reason) {
        GrievanceEntity g = load(grievanceId);
        g.setState("WITHDRAWN");
        g.setResolvedAt(Instant.now());
        g.setResolution(reason == null || reason.isBlank()
                        ? "Withdrawn by the complainant" : reason);
        recordEvent(grievanceId, "WITHDRAWN", reason, false);
        return grievances.save(g);
    }

    // ── The published contact ─────────────────────────────────────────────

    /**
     * Publish or replace this tenant's contact point.
     *
     * <p>Supersedes rather than updates. The address published last year is what
     * a complainant from last year was told to use, and overwriting it in place
     * would erase the record of what was published when.
     */
    @Transactional
    public ComplianceContactEntity publishContact(String displayName, String designation,
                                                  String email, String phone,
                                                  String postalAddress, boolean isDpo,
                                                  boolean basedInIndia) {
        if (isDpo && !basedInIndia) {
            throw new BusinessRuleViolationException(
                "Rule 13 requires a Significant Data Fiduciary's DPO to be based in India");
        }

        contacts.findFirstByActiveToIsNull().ifPresent(existing -> {
            existing.setActiveTo(Instant.now());
            contacts.save(existing);
        });

        ComplianceContactEntity c = new ComplianceContactEntity();
        c.setDisplayName(displayName);
        c.setDesignation(designation);
        c.setEmail(email);
        c.setPhone(phone);
        c.setPostalAddress(postalAddress);
        c.setDpo(isDpo);
        c.setBasedInIndia(basedInIndia);
        c.setActiveFrom(Instant.now());

        log.info("event=compliance.contact.published is_dpo={}", isDpo);
        return contacts.save(c);
    }

    /**
     * Publish a contact for a named tenant — WO-032 / F2, for the onboarding path.
     *
     * <p>Separate from {@link #publishContact} because that method depends on
     * {@code TenantContext} twice over, and neither dependency holds during
     * onboarding. A SUPERADMIN creating a hospital has no tenant in context, so
     * {@code @PrePersist} would stamp a null {@code tenant_id} and leave the
     * contact belonging to nobody. Worse, {@code findFirstByActiveToIsNull} runs
     * unfiltered for a SUPERADMIN, so the supersede step would find and retire
     * some <em>other</em> hospital's published contact — silently un-publishing
     * a tenant that was compliant, while onboarding one that was not.
     *
     * <p>Taking the tenant as a parameter removes both. This is the same reason
     * {@code ComplianceContactJpaRepository.findPublishedFor} exists.
     */
    @Transactional
    public ComplianceContactEntity publishContactForTenant(
            UUID tenantId, String displayName, String designation, String email,
            String phone, String postalAddress, boolean isDpo, boolean basedInIndia) {

        if (tenantId == null) {
            throw new BusinessRuleViolationException(
                "A compliance contact must belong to a named tenant");
        }
        if (isDpo && !basedInIndia) {
            throw new BusinessRuleViolationException(
                "Rule 13 requires a Significant Data Fiduciary's DPO to be based in India");
        }

        contacts.findPublishedFor(tenantId).ifPresent(existing -> {
            existing.setActiveTo(Instant.now());
            contacts.save(existing);
        });

        ComplianceContactEntity c = new ComplianceContactEntity();
        c.setTenantId(tenantId);
        c.setDisplayName(displayName);
        c.setDesignation(designation);
        c.setEmail(email);
        c.setPhone(phone);
        c.setPostalAddress(postalAddress);
        c.setDpo(isDpo);
        c.setBasedInIndia(basedInIndia);
        c.setActiveFrom(Instant.now());

        log.info("event=compliance.contact.published tenant_id={} is_dpo={}", tenantId, isDpo);
        return contacts.save(c);
    }

    @Transactional(readOnly = true)
    public Optional<ComplianceContactEntity> publishedContact() {
        return contacts.findFirstByActiveToIsNull();
    }

    @Transactional(readOnly = true)
    public Optional<ComplianceContactEntity> publishedContactFor(UUID tenantId) {
        return contacts.findPublishedFor(tenantId);
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GrievanceEntity get(UUID id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public List<GrievanceEntity> queue(String state) {
        return state == null || state.isBlank()
            ? grievances.findAllByOrderByReceivedAtDesc()
            : grievances.findByStateOrderByDueAtAsc(state);
    }

    @Transactional(readOnly = true)
    public List<GrievanceEventEntity> timelineFor(UUID grievanceId) {
        return events.findByGrievanceIdOrderByOccurredAtDesc(grievanceId);
    }

    @Transactional(readOnly = true)
    public List<GrievanceEntity> historyFor(UUID patientId) {
        return grievances.findByPatientIdOrderByReceivedAtDesc(patientId);
    }

    // ── The clocks ────────────────────────────────────────────────────────

    /**
     * Surface grievances that are overdue, approaching, or never acknowledged.
     *
     * <p>Runs daily. Publishes gauges rather than only logging, because a
     * complaint sliding past its deadline is an absence, and absences are only
     * visible if something counts them.
     */
    @Scheduled(cron = "0 30 7 * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void reportSlaBreaches() {
        try {
            Instant now = Instant.now();
            List<GrievanceEntity> overdue = grievances.findOverdue(now);
            List<GrievanceEntity> pastTarget = grievances.findPastTarget(now);
            List<GrievanceEntity> unacknowledged =
                grievances.findUnacknowledged(now.minus(ACKNOWLEDGE_WINDOW));

            meterRegistry.gauge("hms_grievances_overdue", overdue.size());
            meterRegistry.gauge("hms_grievances_past_target", pastTarget.size());
            meterRegistry.gauge("hms_grievances_unacknowledged", unacknowledged.size());
            meterRegistry.gauge("hms_grievances_open", grievances.countOpen());
            meterRegistry.gauge("hms_grievances_escalated", grievances.countEscalated());

            for (GrievanceEntity g : overdue) {
                log.error("event=grievance.overdue ref={} tenant_id={} days_open={}",
                          g.getGrievanceRef(), g.getTenantId(),
                          Duration.between(g.getReceivedAt(), now).toDays());
            }
            for (GrievanceEntity g : unacknowledged) {
                log.warn("event=grievance.unacknowledged ref={} days_since_receipt={}",
                         g.getGrievanceRef(),
                         Duration.between(g.getReceivedAt(), now).toDays());
            }
        } catch (RuntimeException e) {
            log.error("event=grievance.sla.check.failed error_type={}",
                      e.getClass().getSimpleName());
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private GrievanceEventEntity recordEvent(UUID grievanceId, String type,
                                             String note, boolean communicated) {
        GrievanceEventEntity e = new GrievanceEventEntity();
        e.setGrievanceId(grievanceId);
        e.setEventType(type);
        e.setNote(note);
        e.setCommunicated(communicated);
        e.setOccurredAt(Instant.now());
        return events.save(e);
    }

    private GrievanceEntity load(UUID id) {
        return grievances.findById(id).orElseThrow(() ->
            new ResourceNotFoundException("Grievance not found"));
    }

    /** Human-quotable, e.g. {@code GRV-20260830-4817}. Random suffix, not a counter. */
    private String nextRef() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String ref = "GRV-" + REF_DATE.format(Instant.now())
                       + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (grievances.findByGrievanceRef(ref).isEmpty()) {
                return ref;
            }
        }
        return "GRV-" + REF_DATE.format(Instant.now())
             + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }
}
