package com.hms.application.compliance;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.compliance.ErasureRequestEntity;
import com.hms.infrastructure.persistence.compliance.ErasureRequestJpaRepository;
import com.hms.infrastructure.persistence.compliance.ErasureTargetEntity;
import com.hms.infrastructure.persistence.compliance.ErasureTargetJpaRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The data principal's rights under the DPDP Act: correction and erasure.
 *
 * <p>{@link ErasureService} knows how to clear the stores. This class owns
 * everything around that: taking the request, proving the requester is who they
 * say, holding the statutory clock, refusing when a retention obligation
 * overrides the right, and making sure a request cannot quietly expire unnoticed.
 *
 * <p>The separation matters. Erasure that runs on an unverified request is not
 * compliance, it is data destruction on a stranger's instruction — and it denies
 * the real patient their own history. So the sweep is deliberately behind a gate
 * that only this class opens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPrincipalRightsService {

    private final ErasureRequestJpaRepository requests;
    private final ErasureTargetJpaRepository targets;
    private final ErasureService erasureService;
    private final AuditorAware<UUID> auditorAware;
    private final MeterRegistry meterRegistry;

    /**
     * DPDP Rules 2025 set a 90-day ceiling for grievance resolution. This project
     * applies the same clock to rights requests as the conservative reading —
     * the Rules do not state a separate period for erasure, and assuming a longer
     * one would be the wrong way to be wrong. Flagged for counsel in WO-024.
     */
    private static final Duration STATUTORY_WINDOW = Duration.ofDays(90);

    private static final Set<String> VERIFICATION_METHODS = Set.of(
        "PORTAL_OTP", "IN_PERSON_ID", "ABHA_VERIFIED", "REGISTERED_POST", "STAFF_OVERRIDE");

    // ── Intake ────────────────────────────────────────────────────────────

    /**
     * Raise an erasure or correction request.
     *
     * <p>Idempotent per patient and type: a patient who asks twice gets the same
     * open request back rather than two racing sweeps. Two concurrent sweeps
     * over the same patient would interleave their target rows and make the
     * audit unreadable.
     */
    @Transactional
    public ErasureRequestEntity raise(UUID patientId, String requestType, String requestedVia,
                                      boolean requestedByPatient,
                                      Map<String, Object> correctionPayload) {

        if (!"ERASURE".equals(requestType) && !"CORRECTION".equals(requestType)) {
            throw new BusinessRuleViolationException(
                "requestType must be ERASURE or CORRECTION");
        }
        if ("CORRECTION".equals(requestType)
                && (correctionPayload == null || correctionPayload.isEmpty())) {
            throw new BusinessRuleViolationException(
                "A correction request must say which fields are wrong and what they should say");
        }

        return requests.findOpenFor(patientId, requestType).orElseGet(() -> {
            ErasureRequestEntity request = new ErasureRequestEntity();
            request.setPatientId(patientId);
            request.setRequestType(requestType);
            request.setState("RECEIVED");
            request.setRequestedAt(Instant.now());
            request.setRequestedVia(requestedVia);
            request.setRequestedByPatient(requestedByPatient);
            request.setCorrectionPayload(correctionPayload);
            request.setDueAt(Instant.now().plus(STATUTORY_WINDOW));

            ErasureRequestEntity saved = requests.save(request);
            meterRegistry.counter("hms_rights_requests_total",
                                  "type", requestType,
                                  "channel", requestedVia == null ? "unknown" : requestedVia)
                         .increment();
            log.info("event=rights.request.raised request_id={} patient_id={} type={} via={} due_at={}",
                     saved.getId(), patientId, requestType, requestedVia, saved.getDueAt());
            return saved;
        });
    }

    // ── Verification ──────────────────────────────────────────────────────

    /**
     * Record that the requester was proved to be the patient.
     *
     * <p>{@code STAFF_OVERRIDE} exists because a patient without a phone, in
     * person, holding physical ID is a real case that must not be turned away.
     * It is logged at WARN and attributed to the staff member precisely because
     * it is the weakest of the methods and the one an audit will look at first.
     */
    @Transactional
    public ErasureRequestEntity verifyRequester(UUID requestId, String method) {
        if (!VERIFICATION_METHODS.contains(method)) {
            throw new BusinessRuleViolationException("Unknown verification method: " + method);
        }

        ErasureRequestEntity request = load(requestId);
        if (request.getRequesterVerifiedAt() != null) {
            return request;   // idempotent
        }
        if (isTerminal(request.getState())) {
            throw new BusinessRuleViolationException(
                "Cannot verify a request that is already " + request.getState());
        }

        UUID verifier = auditorAware.getCurrentAuditor().orElseThrow(() ->
            new BusinessRuleViolationException(
                "Verification must be attributable to an authenticated user"));

        request.setRequesterVerifiedAt(Instant.now());
        request.setVerificationMethod(method);
        request.setVerifiedBy(verifier);
        request.setState("IN_PROGRESS");

        if ("STAFF_OVERRIDE".equals(method)) {
            log.warn("event=rights.verification.override request_id={} verified_by={}",
                     requestId, verifier);
        }
        meterRegistry.counter("hms_rights_verifications_total", "method", method).increment();
        log.info("event=rights.request.verified request_id={} method={} verified_by={}",
                 requestId, method, verifier);
        return requests.save(request);
    }

    // ── Execution ─────────────────────────────────────────────────────────

    /**
     * Run the sweep for a verified erasure request.
     *
     * <p>Returns the per-store outcome so the patient can be told exactly what
     * was erased, what was anonymised and what was kept — which is the part of
     * the right that is usually skipped and is the part that makes a refusal
     * lawful rather than arbitrary.
     */
    @Transactional
    public List<ErasureTargetEntity> execute(UUID requestId) {
        ErasureRequestEntity request = load(requestId);

        if (!"ERASURE".equals(request.getRequestType())) {
            throw new BusinessRuleViolationException(
                "Only an ERASURE request runs a sweep; a CORRECTION is applied to the record itself");
        }
        if (request.getRequesterVerifiedAt() == null) {
            throw new BusinessRuleViolationException(
                "Verify the requester before erasing anything");
        }
        if (isTerminal(request.getState())) {
            // Idempotent: a retried click returns what happened, it does not
            // sweep an already-anonymised patient a second time.
            return targets.findByRequestIdOrderByTargetStore(requestId);
        }

        List<ErasureTargetEntity> results = erasureService.sweep(request);
        requests.save(request);

        long failed = results.stream().filter(t -> "FAILED".equals(t.getOutcome())).count();
        if (failed > 0) {
            log.error("event=rights.erasure.incomplete request_id={} failed_targets={}",
                      requestId, failed);
        }
        return results;
    }

    /**
     * Refuse a request, with a reason the patient will be shown.
     *
     * <p>The reason is mandatory and not defaulted. A refusal without one is
     * indistinguishable from the request being lost, and the patient has no
     * route to challenge what they were never told.
     */
    @Transactional
    public ErasureRequestEntity reject(UUID requestId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleViolationException(
                "A refusal must carry a reason — the patient has to be told why");
        }
        ErasureRequestEntity request = load(requestId);
        if (isTerminal(request.getState())) {
            throw new BusinessRuleViolationException(
                "Request is already " + request.getState());
        }
        request.setState("REJECTED");
        request.setRejectionReason(truncate(reason, 500));
        request.setCompletedAt(Instant.now());

        meterRegistry.counter("hms_rights_requests_total",
                              "type", request.getRequestType(),
                              "channel", "rejected").increment();
        log.info("event=rights.request.rejected request_id={} patient_id={}",
                 requestId, request.getPatientId());
        return requests.save(request);
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ErasureRequestEntity get(UUID requestId) {
        return load(requestId);
    }

    @Transactional(readOnly = true)
    public List<ErasureTargetEntity> targetsFor(UUID requestId) {
        return targets.findByRequestIdOrderByTargetStore(requestId);
    }

    @Transactional(readOnly = true)
    public List<ErasureRequestEntity> queue(String state) {
        return state == null || state.isBlank()
            ? requests.findByStateOrderByRequestedAtAsc("RECEIVED")
            : requests.findByStateOrderByRequestedAtAsc(state);
    }

    @Transactional(readOnly = true)
    public List<ErasureRequestEntity> historyFor(UUID patientId) {
        return requests.findByPatientIdOrderByRequestedAtDesc(patientId);
    }

    // ── The clock ─────────────────────────────────────────────────────────

    /**
     * Surface requests past their statutory deadline.
     *
     * <p>Runs tenant-agnostically from a scheduled thread. A rights request that
     * silently runs past its deadline is the failure this job exists to prevent;
     * nobody notices an absence, so it has to be counted.
     */
    @Scheduled(cron = "0 0 7 * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void reportOverdue() {
        try {
            List<ErasureRequestEntity> overdue = requests.findOverdue(Instant.now());
            meterRegistry.gauge("hms_rights_requests_overdue", overdue.size());
            meterRegistry.gauge("hms_rights_requests_open", requests.countOpen());
            for (ErasureRequestEntity r : overdue) {
                log.error("event=rights.request.overdue request_id={} tenant_id={} type={} due_at={}",
                          r.getId(), r.getTenantId(), r.getRequestType(), r.getDueAt());
            }
        } catch (RuntimeException e) {
            log.error("event=rights.overdue.check.failed error_type={}",
                      e.getClass().getSimpleName());
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private ErasureRequestEntity load(UUID requestId) {
        return requests.findById(requestId).orElseThrow(() ->
            new ResourceNotFoundException("Rights request not found"));
    }

    private static boolean isTerminal(String state) {
        return "COMPLETED".equals(state)
            || "PARTIALLY_COMPLETED".equals(state)
            || "REJECTED".equals(state);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
