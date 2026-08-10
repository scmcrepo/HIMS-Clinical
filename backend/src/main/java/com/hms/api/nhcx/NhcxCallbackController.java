package com.hms.api.nhcx;

import com.hms.api.shared.ApiResponse;
import com.hms.application.claims.NhcxCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Where NHCX posts a payer's answer.
 *
 * <p>Registered as permitAll in SecurityConfig. Public by necessity — the exchange has no session with us — so
 * authentication comes entirely from the JWS signature inside the payload. That
 * is why {@code NhcxPayloadCodec.decryptAndVerify} throwing on a bad signature is
 * the security boundary here, not a nicety.
 *
 * <p>Tenant context is deliberately NOT established here. There is nothing in the
 * request to resolve it from — the correlation id lives inside the encrypted
 * payload, so the tenant cannot be known until after
 * {@code NhcxPayloadCodec.decryptAndVerify} has run. {@code NhcxCallbackService}
 * sets {@code TenantContext} and {@code BranchContext} from the resolved
 * transaction and clears them in a finally block. Resolving tenant at this layer
 * would mean trusting an unverified header.
 *
 * <p>Always answers 202 for a well-formed request, even when processing fails.
 * NHCX retries non-2xx, and a retry storm caused by our own downstream error
 * helps nobody: the correlation record is already persisted, so a failed
 * callback becomes a stuck transaction the timeout sweep escalates, which is a
 * far better outcome than the exchange hammering the endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/nhcx/callback")
@RequiredArgsConstructor
public class NhcxCallbackController {

    private final NhcxCallbackService service;

    /**
     * NHCX response callback.
     *
     * <p>Two spellings of the eligibility path are accepted. The requirement
     * document specifies {@code /on-check} while this controller was originally
     * written against {@code /on_check}, and NHCX's own guide has used both
     * across revisions. Registering both costs nothing and removes an
     * integration failure that would otherwise appear only against the live
     * gateway, as silently-dropped callbacks.
     */
    @PostMapping({"/coverageeligibility/on_check", "/coverageeligibility/on-check",
                  "/on-check", "/on_check",
                  "/preauth/on_submit", "/claim/on_submit",
                  "/discovery/on_discover", "/payment/on_notice"})
    public ResponseEntity<ApiResponse<Void>> onResponse(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "x-hcx-correlation_id", required = false) String correlationId,
            @RequestHeader(value = "x-hcx-sender_code", required = false) String senderCode) {

        String payload = body.get("payload") == null ? null : String.valueOf(body.get("payload"));
        if (payload == null || payload.isBlank()) {
            // Malformed rather than hostile: tell NHCX so, and do not retry-loop.
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing payload"));
        }

        try {
            service.handle(payload, correlationId, senderCode);
        } catch (RuntimeException e) {
            // Logged and swallowed deliberately - see the class javadoc.
            log.error("nhcx.callback.processing_failed correlationId[{}] sender[{}] type[{}]",
                      correlationId, senderCode, e.getClass().getSimpleName());
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.ok("Received"));
    }
}
