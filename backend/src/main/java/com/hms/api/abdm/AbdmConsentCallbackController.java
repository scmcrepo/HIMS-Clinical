package com.hms.api.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.application.abdm.AbdmConsentService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Inbound callbacks from the ABDM Consent Manager.
 *
 * <p>Without this the grant, denial and revocation handlers on
 * {@link AbdmConsentService} are unreachable: a patient could approve a consent
 * on their phone and the hospital would never learn of it, because nothing on
 * our side was listening. Consent requests would sit at PENDING_APPROVAL
 * forever with no error anywhere to explain why.
 *
 * <h2>Why this is permitted differently</h2>
 * The Consent Manager is not an authenticated hospital user. It authenticates
 * as a gateway, which the platform's gateway filter handles; there is no staff
 * session and therefore no {@code hasPermission} check that would mean anything
 * here. {@code permitAll} is the honest expression of that — the protection is
 * the gateway credential and the signature, not a role.
 *
 * <h2>Always 202</h2>
 * A non-2xx makes ABDM retry, and retrying a notification we have already
 * recorded achieves nothing while filling their queue. Failures are recorded and
 * counted on our side rather than pushed back at the gateway. The handlers are
 * idempotent, so a retry that does arrive is harmless.
 */
@Slf4j
@RestController
@RequestMapping("/abdm/callback")
@RequiredArgsConstructor
public class AbdmConsentCallbackController {

    private final AbdmConsentService service;
    private final MeterRegistry meters;

    /**
     * The patient answered a consent request.
     *
     * <p>ABDM sends grants and denials down the same notification path,
     * distinguished only by status, so both are handled here rather than split
     * across endpoints that the gateway would not know to use.
     */
    @PostMapping("/consent/on-notify")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> onConsentNotify(@RequestBody JsonNode body) {
        try {
            JsonNode notification = body.path("notification");
            String status = notification.path("status").asText("");
            String consentRequestId = notification.path("consentRequestId").asText(null);

            if ("DENIED".equalsIgnoreCase(status)) {
                service.recordDenial(consentRequestId);
                count("denied");
                return ResponseEntity.accepted().build();
            }

            if ("REVOKED".equalsIgnoreCase(status)) {
                for (JsonNode artifact : notification.path("consentArtefacts")) {
                    service.recordRevocation(artifact.path("id").asText(null));
                }
                count("revoked");
                return ResponseEntity.accepted().build();
            }

            if ("GRANTED".equalsIgnoreCase(status)) {
                // One artifact per granting provider, so a single grant can
                // produce several rows.
                for (JsonNode artifact : notification.path("consentArtefacts")) {
                    service.recordGrant(
                        consentRequestId,
                        artifact.path("id").asText(null),
                        artifact.path("signature").asText(null),
                        artifact.path("hip").path("id").asText(null),
                        artifact.path("hip").path("name").asText(null),
                        instant(artifact.path("grantedOn").asText(null)),
                        instant(artifact.path("permission").path("dataEraseAt").asText(null)));
                }
                count("granted");
                return ResponseEntity.accepted().build();
            }

            // An unrecognised status is counted rather than ignored: a new ABDM
            // state we do not handle should be visible as a spike, not silence.
            count("unknown_status");
            log.warn("abdm.callback.unknown_status status[{}]", status);
            return ResponseEntity.accepted().build();

        } catch (RuntimeException e) {
            count("failed");
            // Body omitted from the log: it identifies the patient.
            log.error("abdm.callback.consent_failed type[{}]", e.getClass().getSimpleName());
            return ResponseEntity.accepted().build();
        }
    }

    /**
     * Health information is ready to collect.
     *
     * <p>Acknowledged only. The fetch runs on the clinician's action, through
     * {@code AbdmConsentService.fetchRecords}, which re-checks the artifact
     * against the clock — pulling automatically here would bypass that check and
     * could retrieve records under a consent that expired in between.
     */
    @PostMapping("/health-information/on-notify")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> onHealthInformationNotify(@RequestBody JsonNode body) {
        count("hi_notified");
        log.info("abdm.callback.hi_notify transactionId[{}]",
                 body.path("notification").path("transactionId").asText(null));
        return ResponseEntity.accepted().build();
    }

    private void count(String outcome) {
        meters.counter("hms_abdm_callbacks_total", "outcome", outcome).increment();
    }

    private static Instant instant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException e) {
            // A missing expiry is treated as no permission by ConsentArtifactRules,
            // so an unparseable one failing to null is the safe direction.
            return null;
        }
    }
}
