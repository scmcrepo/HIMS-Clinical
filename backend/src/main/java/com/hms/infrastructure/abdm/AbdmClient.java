package com.hms.infrastructure.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.infrastructure.gov.GovApiException;
import com.hms.infrastructure.gov.GovApiProperties;
import com.hms.infrastructure.gov.GovTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ABDM / ABHA gateway client.
 *
 * <p>The ABHA creation flow is: request an OTP against Aadhaar or a mobile
 * number, verify it, then enrol. It is inherently multi-step and stateful across
 * HTTP calls — the gateway hands back a transaction id that must be carried
 * forward — so callers keep that transaction id, not this class.
 *
 * <p><b>Aadhaar is never stored.</b> It is passed through to the gateway for OTP
 * and discarded. What comes back — the ABHA number and address — is what gets
 * persisted, encrypted. Aadhaar appears in no field, no log line and no
 * exception message here.
 *
 * <p><b>Verify paths against the current ABDM implementation guide.</b> ABDM has
 * revised its API surface repeatedly (v1 → v2 → v3 profiles). The paths below
 * reflect the v3 sandbox shape at authoring time and are configurable for
 * exactly that reason.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbdmClient {

    private final GovApiProperties properties;
    private final GovTokenManager tokens;

    /** Result of an OTP request. The transaction id threads the flow together. */
    public record OtpChallenge(String transactionId, String maskedTarget) {
    }

    /** A created or resolved ABHA identity. */
    public record AbhaIdentity(String abhaNumber, String abhaAddress, String transactionId) {
    }

    /**
     * Send an OTP to the mobile linked to an Aadhaar number.
     *
     * @param aadhaar passed through, never stored, never logged
     */
    public OtpChallenge requestAadhaarOtp(String aadhaar) {
        JsonNode body = post("/api/v3/enrollment/request/otp", Map.of(
            "txnId", "",
            "scope", java.util.List.of("abha-enrol"),
            "loginHint", "aadhaar",
            "otpSystem", "aadhaar",
            "loginId", aadhaar));
        return new OtpChallenge(text(body, "txnId"), optional(body, "message"));
    }

    public OtpChallenge requestMobileOtp(String mobile) {
        JsonNode body = post("/api/v3/enrollment/request/otp", Map.of(
            "txnId", "",
            "scope", java.util.List.of("abha-enrol", "mobile-verify"),
            "loginHint", "mobile",
            "otpSystem", "abdm",
            "loginId", mobile));
        return new OtpChallenge(text(body, "txnId"), optional(body, "message"));
    }

    /**
     * Verify an OTP and enrol, producing an ABHA number.
     *
     * <p>ABDM may report that an ABHA already exists for this identity. That is
     * a normal outcome, not an error — most patients over a certain age already
     * have one — so it is surfaced as a populated identity rather than thrown.
     */
    public AbhaIdentity verifyOtpAndEnrol(String transactionId, String otp, String mobile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("txnId", transactionId);
        payload.put("authData", Map.of(
            "authMethods", java.util.List.of("otp"),
            "otp", Map.of("txnId", transactionId, "otpValue", otp,
                          "mobile", mobile == null ? "" : mobile)));
        payload.put("consent", Map.of("code", "abha-enrollment", "version", "1.4"));

        JsonNode body = post("/api/v3/enrollment/enrol/byAadhaar", payload);
        JsonNode profile = body.path("ABHAProfile");

        return new AbhaIdentity(
            profile.path("ABHANumber").asText(null),
            profile.path("phrAddress").isArray() && profile.path("phrAddress").size() > 0
                ? profile.path("phrAddress").get(0).asText(null)
                : null,
            transactionId);
    }

    /** Whether an ABHA address is already taken. Used before suggesting one. */
    public boolean abhaAddressExists(String abhaAddress) {
        try {
            JsonNode body = post("/api/v3/profile/public/certificate",
                                 Map.of("abhaAddress", abhaAddress));
            return body != null && !body.isEmpty();
        } catch (GovApiException e) {
            if ("ABDM_404".equals(e.getCode())) {
                return false;
            }
            throw e;
        }
    }

    // ── transport ────────────────────────────────────────────────────────────

    private JsonNode post(String path, Object body) {
        GovApiProperties.Abdm cfg = properties.getAbdm();
        String url = cfg.getBaseUrl() + path;
        String requestId = UUID.randomUUID().toString();

        try {
            return RestClient.create()
                .post()
                .uri(url)
                .header("Authorization", "Bearer " + tokens.abdmToken())
                .header("REQUEST-ID", requestId)
                .header("TIMESTAMP", java.time.Instant.now().toString())
                .header("X-CM-ID", "sbx")
                .header("X-HIP-ID", cfg.getFacilityId())
                .header("X-Correlation-Id",
                        MDC.get("correlationId") == null ? requestId : MDC.get("correlationId"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            // Token may have expired mid-flight; drop it so the next call re-auths.
            tokens.invalidate("ABDM");
            throw new GovApiException("ABDM_401", "ABDM rejected the credentials", true);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // The response body can echo the Aadhaar that was submitted, so only
            // the status code is logged and only a generic message propagates.
            log.warn("abdm.call.failed path[{}] status[{}] requestId[{}]",
                     path, e.getStatusCode().value(), requestId);
            throw new GovApiException("ABDM_" + e.getStatusCode().value(),
                                      "ABDM rejected the request", false);
        } catch (RuntimeException e) {
            log.error("abdm.call.error path[{}] type[{}] requestId[{}]",
                      path, e.getClass().getSimpleName(), requestId);
            throw new GovApiException("ABDM_UNAVAILABLE", "ABDM is unreachable", true);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new GovApiException("ABDM_RESPONSE_MALFORMED",
                                      "ABDM response did not contain " + field, false);
        }
        return node.get(field).asText();
    }

    private static String optional(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
