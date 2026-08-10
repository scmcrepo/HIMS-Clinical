package com.hms.infrastructure.nhcx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.infrastructure.gov.GovApiException;
import com.hms.infrastructure.gov.GovApiProperties;
import com.hms.infrastructure.gov.GovTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * National Health Claims Exchange client.
 *
 * <p>Two properties dominate the design and both are easy to get wrong.
 *
 * <p><b>It is asynchronous.</b> A submission returns an acknowledgement, not an
 * answer; the payer's response arrives later on a callback. Modelling this as a
 * blocking call produces a system that appears to work in a sandbox and then
 * hangs in production. Every submission therefore persists a correlation record
 * before the HTTP call, and the callback is matched against it.
 *
 * <p><b>Payloads are signed and encrypted between participants.</b> The JWE/JWS
 * assembly is delegated to {@link NhcxPayloadCodec} so key handling lives in one
 * place with one audit surface.
 *
 * <p><b>Verify against the current NHCX implementation guide.</b> Paths, header
 * names and the protected-header field set have all changed across NHCX
 * revisions; everything below is configurable for that reason.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NhcxClient {

    private final GovApiProperties properties;
    private final GovTokenManager tokens;
    private final NhcxPayloadCodec codec;
    private final ObjectMapper objectMapper;

    /** Acknowledgement of receipt. The real answer arrives on the callback. */
    public record Acknowledgement(String correlationId, String apiCallId, int httpStatus) {
    }

    public Acknowledgement submitEligibility(Map<String, Object> bundle, String payerCode,
                                             String correlationId) {
        return submit("/v1/coverageeligibility/check", bundle, payerCode, correlationId);
    }

    /**
     * Ask the registry which policies an identifier is linked to — Screen 1.2.
     *
     * <p>Discovery is broadcast to the participant registry rather than aimed at
     * one payer, because the whole point is that the hospital does not yet know
     * who the insurer is. {@code payerCode} is therefore the registry's own code,
     * not a specific insurer's.
     *
     * <p>The patient authorises this with an OTP first — see
     * {@link #requestDiscoveryOtp}. Querying a person's insurance holdings
     * without their authorisation is a DPDP problem regardless of what the
     * gateway permits.
     */
    public Acknowledgement discoverPolicies(Map<String, Object> bundle, String registryCode,
                                            String correlationId) {
        return submit("/v1/coverageeligibility/discover", bundle, registryCode, correlationId);
    }

    /**
     * Send the patient an OTP authorising a policy lookup.
     *
     * <p>Returns the transaction id that must accompany
     * {@link #confirmDiscoveryOtp}. The identifier — ABHA address or mobile — is
     * forwarded and never logged: it is the thing being protected.
     */
    public Acknowledgement requestDiscoveryOtp(Map<String, Object> bundle, String registryCode,
                                               String correlationId) {
        return submit("/v1/coverageeligibility/discover/otp", bundle, registryCode, correlationId);
    }

    /** Confirm the OTP, releasing the discovery result. */
    public Acknowledgement confirmDiscoveryOtp(Map<String, Object> bundle, String registryCode,
                                               String correlationId) {
        return submit("/v1/coverageeligibility/discover/otp/verify", bundle, registryCode,
                      correlationId);
    }

    public Acknowledgement submitPreAuth(Map<String, Object> bundle, String payerCode,
                                         String correlationId) {
        return submit("/v1/preauth/submit", bundle, payerCode, correlationId);
    }

    public Acknowledgement submitClaim(Map<String, Object> bundle, String payerCode,
                                       String correlationId) {
        return submit("/v1/claim/submit", bundle, payerCode, correlationId);
    }

    private Acknowledgement submit(String path, Map<String, Object> bundle, String payerCode,
                                   String correlationId) {
        GovApiProperties.Nhcx cfg = properties.getNhcx();
        if (!properties.isNhcxConfigured()) {
            throw new GovApiException("NHCX_NOT_CONFIGURED",
                "NHCX credentials are not set. Provide hms.gov.nhcx.* (env: HMS_GOV_NHCX_*).",
                false);
        }

        String apiCallId = UUID.randomUUID().toString();
        String encrypted;
        try {
            encrypted = codec.signAndEncrypt(objectMapper.writeValueAsString(bundle), payerCode);
        } catch (Exception e) {
            // The bundle contains patient data; never let it into a log or message.
            log.error("nhcx.payload.codec.failed path[{}] type[{}]",
                      path, e.getClass().getSimpleName());
            throw new GovApiException("NHCX_PAYLOAD_FAILED",
                                      "Could not sign or encrypt the claim payload", false);
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("payload", encrypted);

        try {
            RestClient.create()
                .post()
                .uri(cfg.getBaseUrl() + path)
                .header("Authorization", "Bearer " + tokens.nhcxToken())
                .header("x-hcx-sender_code", cfg.getParticipantCode())
                .header("x-hcx-recipient_code", payerCode)
                .header("x-hcx-correlation_id", correlationId)
                .header("x-hcx-api_call_id", apiCallId)
                .header("x-hcx-timestamp", String.valueOf(System.currentTimeMillis()))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(envelope)
                .retrieve()
                .toBodilessEntity();

            log.info("nhcx.submitted path[{}] correlationId[{}] apiCallId[{}] payer[{}]",
                     path, correlationId, apiCallId, payerCode);
            return new Acknowledgement(correlationId, apiCallId, 202);

        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            tokens.invalidate("NHCX");
            throw new GovApiException("NHCX_401", "NHCX rejected the credentials", true);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("nhcx.rejected path[{}] status[{}] correlationId[{}]",
                     path, e.getStatusCode().value(), correlationId);
            // 4xx is the gateway refusing this submission — retrying is pointless
            // and would just re-file a rejected claim.
            throw new GovApiException("NHCX_" + e.getStatusCode().value(),
                                      "NHCX rejected the submission", false);
        } catch (RuntimeException e) {
            log.error("nhcx.unavailable path[{}] type[{}] correlationId[{}]",
                      path, e.getClass().getSimpleName(), correlationId);
            throw new GovApiException("NHCX_UNAVAILABLE", "NHCX is unreachable", true);
        }
    }
}
