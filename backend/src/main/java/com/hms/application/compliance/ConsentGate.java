package com.hms.application.compliance;

import com.hms.api.shared.ConsentAttestation;
import com.hms.exception.BusinessRuleViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The one place a service asks "may I do this to this patient's data?".
 *
 * <p>Before WO-022 each service answered that question for itself, and all three
 * answered it the same wrong way:
 *
 * <pre>
 *   if (!consent.hasConsent(p, PURPOSE)) { consent.grant(p, PURPOSE, ...); }
 *   consent.requireConsent(p, PURPOSE);
 * </pre>
 *
 * <p>The check could not fail because the branch above it removed the only
 * condition under which it would. Centralising the decision here means there is
 * exactly one implementation to audit, and
 * {@code ConsentGateStaticConventionTest} asserts no service reintroduces its
 * own.
 *
 * <p>The capturing user is read from the security context, never passed in by a
 * caller. That is what makes {@code captured_by} evidence rather than an
 * assertion the client made about itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentGate {

    private final ConsentService consentService;
    private final AuditorAware<UUID> auditorAware;

    /**
     * Ensure consent exists for this purpose, capturing it first if the caller
     * supplied a staff attestation.
     *
     * @param attestation may be null — meaning the caller has nothing new to
     *                    record, so existing consent must already cover it
     * @param action      short label for the metric and log, e.g.
     *                    {@code "abha.enrolment"}
     * @throws ConsentRequiredException      no consent and no attestation
     * @throws BusinessRuleViolationException an attestation arrived but no
     *                                        authenticated user could be
     *                                        identified to attach to it
     */
    public void ensure(UUID patientId, ConsentPurpose purpose,
                       ConsentAttestation attestation, String action) {

        if (consentService.hasConsent(patientId, purpose)) {
            return;
        }

        if (attestation == null) {
            // Throws, carrying the notice text the desk needs to show. In
            // warn mode this returns instead, having metered the refusal.
            consentService.requireConsent(patientId, purpose, action);
            return;
        }

        UUID capturedBy = auditorAware.getCurrentAuditor().orElseThrow(() ->
            new BusinessRuleViolationException(
                "Consent cannot be recorded without an authenticated user to attribute it to"));

        consentService.captureFromAttestation(
            patientId, purpose,
            attestation.noticeVersion(), attestation.noticeLanguage(),
            "IN_PERSON", capturedBy,
            attestation.minor(), attestation.guardianVerified());

        log.info("event=consent.captured patient_id={} purpose={} action={} captured_by={}",
                 patientId, purpose, action, capturedBy);
    }
}
