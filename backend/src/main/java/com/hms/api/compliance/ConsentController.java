package com.hms.api.compliance;

import com.hms.api.compliance.request.ConsentRequests;
import com.hms.api.compliance.response.ConsentResponses;
import com.hms.api.shared.ApiResponse;
import com.hms.application.compliance.ConsentPurpose;
import com.hms.application.compliance.ConsentService;
import com.hms.infrastructure.persistence.compliance.ConsentRecordEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.hms.exception.BusinessRuleViolationException;

/**
 * Consent management — WO-023.
 *
 * <p>Completes the surface WO-022 started. That work order made the gate capable
 * of failing and let the desk capture consent inline at the point of an action;
 * this one lets anyone see what a patient has agreed to, capture consent outside
 * a blocked action, and — the part that matters most — withdraw it.
 *
 * <p>Withdrawal is deliberately the least ceremonious endpoint here. Consent that
 * is harder to withdraw than to give is not freely given, so there is no
 * confirmation step, no reason field, and no separate approval. The patient said
 * stop.
 *
 * <p>Two permissions. {@code CONSENT_VIEW} is wide, because any clinician about
 * to send an automated reminder needs to check whether they may.
 * {@code CONSENT_MANAGE} is narrow, because recording agreement on a patient's
 * behalf is a materially different act from reading it.
 */
@RestController
@RequestMapping("/compliance/consent")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;
    private final AuditorAware<UUID> auditorAware;

    /**
     * Everything this patient has agreed to, and everything they have revoked.
     *
     * <p>Returns the full history rather than the live state alone. "Did they
     * ever consent to voice calls, and when did they stop?" is the question an
     * audit asks, and a current-state-only view cannot answer it.
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasPermission('CONSENT_VIEW','')")
    public ResponseEntity<ApiResponse<List<ConsentResponses.ConsentRecord>>> history(
            @PathVariable UUID patientId) {

        List<ConsentResponses.ConsentRecord> rows = consentService.historyFor(patientId).stream()
            .map(ConsentResponses.ConsentRecord::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.of(rows));
    }

    /**
     * The live consent state across every purpose, for a consent screen.
     *
     * <p>Each row carries the notice the patient would be shown if they were
     * asked now, so the screen does not need a second call per purpose.
     */
    @GetMapping("/patient/{patientId}/status")
    @PreAuthorize("hasPermission('CONSENT_VIEW','')")
    public ResponseEntity<ApiResponse<List<ConsentResponses.PurposeStatus>>> status(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "en") String language) {

        List<ConsentResponses.PurposeStatus> rows = Arrays.stream(ConsentPurpose.values())
            .map(purpose -> ConsentResponses.PurposeStatus.of(
                purpose,
                consentService.hasConsent(patientId, purpose),
                consentService.activeNotice(purpose, language).orElse(null)))
            .toList();
        return ResponseEntity.ok(ApiResponse.of(rows));
    }

    /**
     * Capture consent outside a blocked action.
     *
     * <p>For the registration desk working through a consent form, rather than
     * the mid-action 409 path WO-022 built. Same underlying attestation rules:
     * the capturing user comes from the session, and the hash is computed over
     * the server's notice text.
     */
    @PostMapping("/patient/{patientId}/grant")
    @PreAuthorize("hasPermission('CONSENT_MANAGE','')")
    public ResponseEntity<ApiResponse<ConsentResponses.ConsentRecord>> grant(
            @PathVariable UUID patientId,
            @Valid @RequestBody ConsentRequests.Grant body) {

        UUID capturedBy = auditorAware.getCurrentAuditor().orElseThrow(() ->
            new BusinessRuleViolationException(
                "Consent cannot be recorded without an authenticated user to attribute it to"));

        ConsentRecordEntity saved = consentService.captureFromAttestation(
            patientId, body.purpose(), body.noticeVersion(), body.noticeLanguage(),
            body.captureChannel(), capturedBy, body.minor(), body.guardianVerified());

        return ResponseEntity.ok(ApiResponse.ok(
            "Consent recorded", ConsentResponses.ConsentRecord.from(saved)));
    }

    /**
     * Withdraw consent for one purpose.
     *
     * <p>Idempotent, and deliberately free of ceremony. Withdrawing consent must
     * be at least as easy as giving it, so there is no confirmation body and no
     * reason field to fill in. The record is not deleted — it is marked
     * WITHDRAWN, because the row is the evidence that consent existed and was
     * revoked.
     */
    @PostMapping("/patient/{patientId}/withdraw")
    @PreAuthorize("hasPermission('CONSENT_MANAGE','')")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @PathVariable UUID patientId,
            @Valid @RequestBody ConsentRequests.Withdraw body) {

        consentService.withdraw(patientId, body.purpose(),
                                body.channel() == null ? "STAFF_PORTAL" : body.channel(),
                                auditorAware.getCurrentAuditor().orElse(null));

        return ResponseEntity.ok(ApiResponse.ok("Consent withdrawn"));
    }

    /**
     * The notice text for a purpose, so a client can show it before asking.
     *
     * <p>Served from the registry rather than a constant, because each tenant
     * supplies its own wording and the hash stored against a consent record is
     * computed over the server's copy. A client with hard-coded text could show
     * something the record then claims was shown when it was not.
     */
    @GetMapping("/notice")
    @PreAuthorize("hasPermission('CONSENT_VIEW','')")
    public ResponseEntity<ApiResponse<ConsentResponses.Notice>> notice(
            @RequestParam ConsentPurpose purpose,
            @RequestParam(defaultValue = "en") String language) {

        return consentService.activeNotice(purpose, language)
            .map(n -> ResponseEntity.ok(ApiResponse.of(ConsentResponses.Notice.from(n))))
            .orElseThrow(() -> new BusinessRuleViolationException(
                "No notice text on file for " + purpose + " in " + language
                + " — consent cannot be captured against a notice this hospital "
                + "cannot produce"));
    }
}
