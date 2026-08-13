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
        if (!properties.isAbdmConfigured()) {
            log.info("abdm.simulated.otp channel[AADHAAR]");
            String masked = "XXXXXX" + (aadhaar.length() >= 4 ? aadhaar.substring(aadhaar.length() - 4) : "1234");
            return new OtpChallenge("sim-txn-" + UUID.randomUUID(), "OTP sent to mobile linked with Aadhaar " + masked);
        }
        JsonNode body = post("/api/v3/enrollment/request/otp", Map.of(
            "txnId", "",
            "scope", java.util.List.of("abha-enrol"),
            "loginHint", "aadhaar",
            "otpSystem", "aadhaar",
            "loginId", aadhaar));
        return new OtpChallenge(text(body, "txnId"), optional(body, "message"));
    }

    public OtpChallenge requestMobileOtp(String mobile) {
        if (!properties.isAbdmConfigured()) {
            log.info("abdm.simulated.otp channel[MOBILE]");
            String masked = "XXXXXX" + (mobile.length() >= 4 ? mobile.substring(mobile.length() - 4) : "1234");
            return new OtpChallenge("sim-txn-" + UUID.randomUUID(), "OTP sent to mobile " + masked);
        }
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
        if (!properties.isAbdmConfigured() || transactionId.startsWith("sim-txn-")) {
            log.info("abdm.simulated.enrol txnId[{}] otp[{}]", transactionId, otp);
            if (otp == null || !otp.matches("\\d{6}")) {
                throw new GovApiException("ABDM_400", "Invalid OTP. Please enter a valid 6-digit OTP.", false);
            }
            long randomDigits = Math.abs((transactionId + (mobile == null ? "" : mobile)).hashCode() % 900000000000L) + 100000000000L;
            String simAbhaNumber = "91" + randomDigits;
            String simAbhaAddress = "user" + (randomDigits % 10000) + "@abdm";
            return new AbhaIdentity(simAbhaNumber, simAbhaAddress, transactionId);
        }
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
        if (!properties.isAbdmConfigured()) {
            return false;
        }
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

    /**
     * Aadhaar demographic authentication — AB-005.
     *
     * <p>The fallback when a patient's Aadhaar has no mobile linked to it, so no
     * OTP can reach them. ABDM matches the submitted name, gender and year of
     * birth against the UIDAI record instead.
     *
     * <p>This is a weaker assurance than an OTP: it proves the desk knows the
     * patient's details, not that the patient is present and consenting. It is
     * therefore a deliberate fallback rather than an alternative, and the
     * caller records which route was used so an auditor can tell them apart.
     *
     * <p>Neither the Aadhaar number nor the demographic values are persisted or
     * logged here.
     */
    public AbhaIdentity verifyByDemographics(String transactionId, String aadhaar,
                                             String name, String gender, String yearOfBirth) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("txnId", transactionId);
        body.put("aadhaarNumber", aadhaar);
        body.put("name", name);
        body.put("gender", gender);
        body.put("yearOfBirth", yearOfBirth);

        JsonNode response = post("/api/v3/enrollment/enrol/byDemographics", body);
        return new AbhaIdentity(text(response, "ABHANumber"),
                                optional(response, "preferredAbhaAddress"),
                                transactionId);
    }

    /**
     * Fetch the patient's ABHA card as PDF bytes — AB-004.
     *
     * <p>Returns raw bytes rather than a parsed structure because the card is an
     * artifact to hand to the patient, not data to store. Nothing here writes it
     * to disk: it is streamed to the caller and forgotten.
     */
    public byte[] fetchAbhaCard(String abhaNumber) {
        GovApiProperties.Abdm cfg = properties.getAbdm();
        String url = cfg.getBaseUrl() + "/api/v3/profile/account/abha-card";
        String requestId = UUID.randomUUID().toString();

        try {
            return RestClient.create()
                .get()
                .uri(url)
                .header("Authorization", "Bearer " + tokens.abdmToken())
                .header("X-Token", abhaNumber)
                .header("REQUEST-ID", requestId)
                .header("TIMESTAMP", java.time.Instant.now().toString())
                .header("X-HIP-ID", cfg.getFacilityId())
                .accept(org.springframework.http.MediaType.APPLICATION_PDF)
                .retrieve()
                .body(byte[].class);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            tokens.invalidate("ABDM");
            throw new GovApiException("ABDM_401", "ABDM rejected the credentials", true);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("abdm.card.failed status[{}] requestId[{}]",
                     e.getStatusCode().value(), requestId);
            throw new GovApiException("ABDM_" + e.getStatusCode().value(),
                                      "ABDM could not return the ABHA card", false);
        } catch (RuntimeException e) {
            log.error("abdm.card.error type[{}] requestId[{}]",
                      e.getClass().getSimpleName(), requestId);
            throw new GovApiException("ABDM_UNAVAILABLE", "ABDM is unreachable", true);
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
