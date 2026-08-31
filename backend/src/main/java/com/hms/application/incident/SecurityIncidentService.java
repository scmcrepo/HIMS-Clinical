package com.hms.application.incident;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.incident.IncidentAffectedPrincipalEntity;
import com.hms.infrastructure.persistence.incident.IncidentAffectedPrincipalJpaRepository;
import com.hms.infrastructure.persistence.incident.SecurityIncidentEntity;
import com.hms.infrastructure.persistence.incident.SecurityIncidentJpaRepository;
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Breach detection, containment and notification — DPDP s. 8(6) and Rule 7.
 *
 * <p>Built for WO-026, prompted by WO-028 finding that
 * {@code AbdmConsentCallbackController} accepted unauthenticated cross-tenant
 * writes. Had that been exploited, the hospital would have had a reportable
 * breach and no mechanism whatsoever to report it — no register, no way to
 * establish who was affected, no notification path.
 *
 * <p>Rule 7 runs two clocks from the moment of awareness: an initial intimation
 * to the Board without delay, and a fuller report within 72 hours. They are
 * tracked separately because they are missed independently, and a system that
 * collapses them into one "notified" flag cannot tell you which obligation you
 * are about to breach.
 *
 * <h2>What this class will not do</h2>
 *
 * <p>It does not auto-notify the Board. Notification is an irreversible external
 * act with legal consequences, and a false positive that files a breach report
 * on a hospital's behalf would be its own serious incident. Detection raises an
 * incident and shouts; a human decides.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityIncidentService {

    private final SecurityIncidentJpaRepository incidents;
    private final IncidentAffectedPrincipalJpaRepository affected;
    private final AuditorAware<UUID> auditorAware;
    private final MeterRegistry meterRegistry;

    /**
     * Initial Board intimation is due "without delay". Encoded as 24 hours for
     * alerting purposes only — the alert should fire well before the obligation
     * is actually breached, not at the moment it is.
     */
    private static final Duration BOARD_INITIAL_WINDOW = Duration.ofHours(24);

    /** Fuller report to the Board. Confirm the exact period with counsel. */
    private static final Duration BOARD_DETAIL_WINDOW = Duration.ofHours(72);

    private static final DateTimeFormatter REF_DATE =
        DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    // ── Raising ───────────────────────────────────────────────────────────

    /**
     * Record an incident.
     *
     * <p>Deliberately easy to call and widely permissioned. A near-miss nobody
     * could file is a near-miss nobody learns from, and the cost of an
     * over-reported incident is a triage conversation, while the cost of an
     * unreported one is a statutory breach.
     */
    @Transactional
    public SecurityIncidentEntity raise(String category, String severity, String summary,
                                        String detail, String detectionSource,
                                        String dataCategories, Instant detectedAt,
                                        boolean scopeUncertain) {

        SecurityIncidentEntity incident = new SecurityIncidentEntity();
        incident.setIncidentRef(nextRef());
        incident.setCategory(category);
        incident.setSeverity(severity);
        incident.setSummary(truncate(summary, 500));
        incident.setDetail(detail);
        incident.setDetectionSource(detectionSource);
        incident.setDataCategories(truncate(dataCategories, 300));
        // Never defaults to now() silently: if the caller knows when we became
        // aware, that is the moment the clocks start, and quietly resetting it
        // to the filing time would give back hours the hospital does not have.
        incident.setDetectedAt(detectedAt == null ? Instant.now() : detectedAt);
        incident.setScopeUncertain(scopeUncertain);
        incident.setState("OPEN");

        SecurityIncidentEntity saved = incidents.save(incident);

        meterRegistry.counter("hms_security_incidents_total",
                              "category", category, "severity", severity).increment();
        // ERROR regardless of severity. An incident is not a routine event, and
        // the log level is what decides whether anyone is paged tonight.
        log.error("event=incident.raised ref={} category={} severity={} source={} scope_uncertain={}",
                  saved.getIncidentRef(), category, severity, detectionSource, scopeUncertain);
        return saved;
    }

    /**
     * Record the affected people.
     *
     * <p>Ids only. Contact details are read from the patient record at send time
     * rather than copied here — a breach register that accumulates personal data
     * enlarges the problem it exists to manage.
     */
    @Transactional
    public int recordAffectedPatients(UUID incidentId, Collection<UUID> patientIds) {
        SecurityIncidentEntity incident = load(incidentId);
        if (isTerminal(incident.getState())) {
            throw new BusinessRuleViolationException(
                "Cannot add affected people to an incident that is already " + incident.getState());
        }

        int added = 0;
        for (UUID patientId : patientIds) {
            IncidentAffectedPrincipalEntity row = new IncidentAffectedPrincipalEntity();
            row.setIncidentId(incidentId);
            row.setPatientId(patientId);
            row.setNotificationState("PENDING");
            affected.save(row);
            added++;
        }

        incident.setAffectedPrincipalCount(
            incident.getAffectedPrincipalCount() + added);
        incidents.save(incident);

        log.warn("event=incident.scope.updated ref={} added={} total={}",
                 incident.getIncidentRef(), added, incident.getAffectedPrincipalCount());
        return added;
    }

    // ── Containment and notification ──────────────────────────────────────

    @Transactional
    public SecurityIncidentEntity markContained(UUID incidentId, String remediation) {
        SecurityIncidentEntity incident = load(incidentId);
        incident.setContainedAt(Instant.now());
        incident.setState("CONTAINED");
        incident.setRemediation(remediation);

        log.warn("event=incident.contained ref={} elapsed_minutes={}",
                 incident.getIncidentRef(),
                 Duration.between(incident.getDetectedAt(), Instant.now()).toMinutes());
        return incidents.save(incident);
    }

    /**
     * Record that the Board has been told.
     *
     * <p>Records rather than sends. There is no Board API to call, and inventing
     * an automated filing would be worse than useless — it would let the system
     * claim an obligation was discharged when a human never checked what was
     * being reported.
     *
     * @param boardReference the acknowledgement reference the Board returned,
     *                       which is the only durable proof the filing happened
     */
    @Transactional
    public SecurityIncidentEntity recordBoardNotification(UUID incidentId, String boardReference,
                                                          boolean isDetailReport) {
        SecurityIncidentEntity incident = load(incidentId);

        if (isDetailReport) {
            if (incident.getBoardNotifiedAt() == null) {
                throw new BusinessRuleViolationException(
                    "The initial intimation must be filed before the detailed report");
            }
            incident.setBoardDetailReportAt(Instant.now());
        } else {
            incident.setBoardNotifiedAt(Instant.now());
        }
        if (boardReference != null && !boardReference.isBlank()) {
            incident.setBoardReference(truncate(boardReference, 80));
        }

        long hours = Duration.between(incident.getDetectedAt(), Instant.now()).toHours();
        meterRegistry.counter("hms_incident_board_notifications_total",
                              "type", isDetailReport ? "detail" : "initial").increment();
        log.warn("event=incident.board_notified ref={} type={} hours_since_detection={}",
                 incident.getIncidentRef(), isDetailReport ? "detail" : "initial", hours);
        return incidents.save(incident);
    }

    /**
     * Mark affected people as notified.
     *
     * <p>Per-person rather than a single flag on the incident, because "we sent
     * a notice" and "everyone received one" are different claims and only the
     * second discharges the duty. Failures stay FAILED and keep the incident
     * open.
     */
    @Transactional
    public NotificationOutcome recordPrincipalNotifications(UUID incidentId, String channel) {
        SecurityIncidentEntity incident = load(incidentId);
        List<IncidentAffectedPrincipalEntity> pending =
            affected.findByIncidentIdAndNotificationState(incidentId, "PENDING");

        int sent = 0;
        for (IncidentAffectedPrincipalEntity row : pending) {
            row.setNotifiedAt(Instant.now());
            row.setNotificationChannel(channel);
            row.setNotificationState("SENT");
            affected.save(row);
            sent++;
        }

        long failed = affected.countByIncidentIdAndNotificationState(incidentId, "FAILED");
        if (failed == 0) {
            incident.setPrincipalsNotifiedAt(Instant.now());
            if (!"CLOSED".equals(incident.getState())) {
                incident.setState("NOTIFIED");
            }
        } else {
            // Deliberately does not set principalsNotifiedAt. The DB constraint
            // then keeps the incident from being closed, which is the point.
            log.error("event=incident.notification.incomplete ref={} failed={}",
                      incident.getIncidentRef(), failed);
        }
        incidents.save(incident);

        meterRegistry.counter("hms_incident_principal_notifications_total",
                              "channel", channel).increment(sent);
        log.warn("event=incident.principals_notified ref={} sent={} failed={}",
                 incident.getIncidentRef(), sent, failed);
        return new NotificationOutcome(sent, (int) failed);
    }

    public record NotificationOutcome(int sent, int failed) {}

    /**
     * The notice text owed to an affected person under Rule 7.
     *
     * <p>Four things are mandatory: the nature of the breach, its likely
     * consequences, what is being done about it, and who to contact. Generated
     * here rather than left to whoever is drafting an email at 2am, because the
     * element most often dropped under pressure is the consequences — the part
     * that lets someone decide whether to act.
     *
     * <p>Plain language, deliberately. A notice the recipient cannot understand
     * has not informed anyone.
     */
    @Transactional(readOnly = true)
    public String draftPrincipalNotice(UUID incidentId, String contactPoint) {
        SecurityIncidentEntity i = load(incidentId);

        StringBuilder b = new StringBuilder();
        b.append("We are writing to tell you about a problem affecting your personal information.\n\n");
        b.append("What happened\n").append(i.getSummary()).append("\n\n");

        if (i.getDataCategories() != null && !i.getDataCategories().isBlank()) {
            b.append("What information was involved\n").append(i.getDataCategories()).append("\n\n");
        }

        b.append("What this could mean for you\n");
        b.append(consequencesFor(i.getCategory()));
        if (i.isScopeUncertain()) {
            // Saying so is uncomfortable and necessary. A person told the scope
            // is unknown can protect themselves; one falsely reassured cannot.
            b.append(" We are still establishing the full extent, and we will "
                     + "write again when we know more.");
        }
        b.append("\n\n");

        b.append("What we are doing\n");
        b.append(i.getRemediation() == null || i.getRemediation().isBlank()
                 ? "We have begun an investigation and are taking steps to prevent this happening again."
                 : i.getRemediation());
        b.append("\n\n");

        b.append("Who to contact\n");
        b.append(contactPoint == null || contactPoint.isBlank()
                 ? "Please contact the hospital's data protection contact."
                 : contactPoint);
        b.append("\n\nYou may also complain to the Data Protection Board of India.\n");
        b.append("\nReference: ").append(i.getIncidentRef()).append('\n');
        return b.toString();
    }

    private static String consequencesFor(String category) {
        return switch (category) {
            case "CROSS_TENANT_ACCESS", "UNAUTHORISED_ACCESS", "DATA_EXPOSURE" ->
                "Someone may have been able to see information about you that they "
                + "should not have, which could include your contact details or "
                + "details of your care.";
            case "CREDENTIAL_COMPROMISE" ->
                "Someone may have been able to sign in as you. If you use the same "
                + "password anywhere else, change it there too.";
            case "INTEGRITY_COMPROMISE" ->
                "Some of your information may have been changed without permission. "
                + "Please check that your records are correct.";
            case "DATA_LOSS" ->
                "Some of your information may no longer be available to us, which "
                + "could affect the completeness of your records.";
            default ->
                "Your personal information may have been affected.";
        };
    }

    @Transactional
    public SecurityIncidentEntity close(UUID incidentId, String rootCause) {
        SecurityIncidentEntity incident = load(incidentId);

        // Mirrors the database CHECK, so the caller gets a sentence rather than
        // a constraint violation.
        if (incident.getAffectedPrincipalCount() > 0
                && (incident.getBoardNotifiedAt() == null
                    || incident.getPrincipalsNotifiedAt() == null)) {
            throw new BusinessRuleViolationException(
                "An incident with affected people cannot be closed until both the "
                + "Board and those people have been notified");
        }
        incident.setState("CLOSED");
        incident.setRootCause(rootCause);
        log.warn("event=incident.closed ref={}", incident.getIncidentRef());
        return incidents.save(incident);
    }

    /** Not a breach after investigation. Kept, never deleted. */
    @Transactional
    public SecurityIncidentEntity dismiss(UUID incidentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolationException(
                "Dismissing an incident requires a reason — this is the record of why "
                + "someone decided it was not a breach");
        }
        SecurityIncidentEntity incident = load(incidentId);
        incident.setState("DISMISSED");
        incident.setRootCause(reason);
        log.warn("event=incident.dismissed ref={} by={}",
                 incident.getIncidentRef(), auditorAware.getCurrentAuditor().orElse(null));
        return incidents.save(incident);
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SecurityIncidentEntity get(UUID id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public List<SecurityIncidentEntity> queue(String state) {
        return state == null || state.isBlank()
            ? incidents.findAllByOrderByDetectedAtDesc()
            : incidents.findByStateOrderByDetectedAtDesc(state);
    }

    @Transactional(readOnly = true)
    public List<IncidentAffectedPrincipalEntity> affectedFor(UUID incidentId) {
        return affected.findByIncidentId(incidentId);
    }

    // ── The clocks ────────────────────────────────────────────────────────

    /**
     * Surface incidents whose statutory notification is overdue.
     *
     * <p>Hourly rather than daily. A 72-hour obligation checked once a day can
     * be a third breached before anyone is told.
     */
    @Scheduled(cron = "0 5 * * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void reportOverdueNotifications() {
        try {
            Instant now = Instant.now();
            List<SecurityIncidentEntity> initialOverdue =
                incidents.findBoardNotificationOverdue(now.minus(BOARD_INITIAL_WINDOW));
            List<SecurityIncidentEntity> detailOverdue =
                incidents.findDetailReportOverdue(now.minus(BOARD_DETAIL_WINDOW));

            meterRegistry.gauge("hms_incident_board_notification_overdue", initialOverdue.size());
            meterRegistry.gauge("hms_incident_detail_report_overdue", detailOverdue.size());
            meterRegistry.gauge("hms_security_incidents_open", incidents.countOpen());

            for (SecurityIncidentEntity i : initialOverdue) {
                log.error("event=incident.board_notification.overdue ref={} hours_since_detection={}",
                          i.getIncidentRef(),
                          Duration.between(i.getDetectedAt(), now).toHours());
            }
            for (SecurityIncidentEntity i : detailOverdue) {
                log.error("event=incident.detail_report.overdue ref={} hours_since_detection={}",
                          i.getIncidentRef(),
                          Duration.between(i.getDetectedAt(), now).toHours());
            }
        } catch (RuntimeException e) {
            log.error("event=incident.overdue.check.failed error_type={}",
                      e.getClass().getSimpleName());
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private SecurityIncidentEntity load(UUID id) {
        return incidents.findById(id).orElseThrow(() ->
            new ResourceNotFoundException("Incident not found"));
    }

    /**
     * A reference a human can read over the phone, e.g. {@code INC-20260830-4817}.
     *
     * <p>Random suffix rather than a sequence: a monotonic counter would tell
     * anyone holding one reference roughly how many incidents there have been.
     */
    private String nextRef() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String ref = "INC-" + REF_DATE.format(Instant.now())
                       + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (incidents.findByIncidentRef(ref).isEmpty()) {
                return ref;
            }
        }
        return "INC-" + REF_DATE.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static boolean isTerminal(String state) {
        return "CLOSED".equals(state) || "DISMISSED".equals(state);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
