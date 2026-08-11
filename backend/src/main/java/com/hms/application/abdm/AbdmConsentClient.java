package com.hms.application.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.infrastructure.gov.GovApiException;
import com.hms.infrastructure.gov.GovApiProperties;
import com.hms.infrastructure.gov.GovTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ABDM Consent Manager and Health Information User transport.
 *
 * <p>Separate from {@link com.hms.infrastructure.abdm.AbdmClient}, which handles
 * ABHA enrolment. Different gateway surface, different lifecycle, and mixing
 * them would produce one class where a change to identity creation could break
 * record retrieval.
 *
 * <p><b>Verify paths against the current ABDM implementation guide.</b> The
 * consent and data-flow APIs have changed shape across ABDM revisions; the paths
 * below reflect the v3 sandbox at authoring time and are grouped here so a
 * revision touches one file.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AbdmConsentClient {

    private final GovApiProperties properties;
    private final GovTokenManager tokens;

    /** One record as a HIP returned it. */
    public record ExternalRecord(String hiType, Instant recordDate, String payload,
                                 String displayTitle) {
    }

    /**
     * Raise a consent request with the Consent Manager.
     *
     * @return the Consent Manager's request id
     */
    public String initConsentRequest(UUID patientId, String purposeCode, Set<String> hiTypes,
                                     LocalDate from, LocalDate to, Instant expiresAt,
                                     String correlationId) {

        Map<String, Object> purpose = new LinkedHashMap<>();
        purpose.put("text", purposeCode);
        purpose.put("code", purposeCode);

        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("accessMode", "VIEW");
        permission.put("dateRange", Map.of("from", from.toString(), "to", to.toString()));
        permission.put("dataEraseAt", expiresAt.toString());

        Map<String, Object> consent = new LinkedHashMap<>();
        consent.put("purpose", purpose);
        consent.put("hiTypes", List.copyOf(hiTypes));
        consent.put("permission", permission);

        JsonNode response = post("/api/v3/hiu/consent-requests/init",
                                 Map.of("consent", consent), correlationId);
        return text(response, "consentRequestId");
    }

    /** Current status of a consent request, for polling while the patient decides. */
    public String consentStatus(String consentRequestId) {
        JsonNode response = post("/api/v3/hiu/consent-requests/status",
                                 Map.of("consentRequestId", consentRequestId), null);
        return text(response, "status");
    }

    /**
     * Fetch records under a granted artifact.
     *
     * <p>The caller filters what comes back against the consented types and date
     * range. A HIP that over-shares is a real occurrence, and this class
     * deliberately does not decide what is in scope — that rule lives in
     * {@link ConsentArtifactRules} where it is tested.
     */
    public List<ExternalRecord> fetchRecords(String artifactId, String signature) {
        JsonNode response = post("/api/v3/hiu/health-information/fetch",
                                 Map.of("consent", Map.of("id", artifactId),
                                        "signature", signature == null ? "" : signature),
                                 null);

        List<ExternalRecord> out = new ArrayList<>();
        if (response == null) {
            return out;
        }

        for (JsonNode entry : response.path("entries")) {
            out.add(new ExternalRecord(
                entry.path("hiType").asText(null),
                parseInstant(entry.path("date").asText(null)),
                entry.path("content").toString(),
                entry.path("title").asText(null)));
        }
        return out;
    }

    // ── transport ────────────────────────────────────────────────────────────

    private JsonNode post(String path, Object body, String correlationId) {
        GovApiProperties.Abdm cfg = properties.getAbdm();
        String url = cfg.getBaseUrl() + path;
        String requestId = UUID.randomUUID().toString();

        try {
            return RestClient.create()
                .post()
                .uri(url)
                .header("Authorization", "Bearer " + tokens.abdmToken())
                .header("REQUEST-ID", requestId)
                .header("TIMESTAMP", Instant.now().toString())
                .header("X-CM-ID", "sbx")
                .header("X-HIU-ID", cfg.getFacilityId())
                .header("X-Correlation-Id",
                        correlationId != null ? correlationId
                            : (MDC.get("correlationId") == null ? requestId
                                                                : MDC.get("correlationId")))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            tokens.invalidate("ABDM");
            throw new GovApiException("ABDM_401", "ABDM rejected the credentials", true);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // The body can carry patient identifiers and clinical detail, so only
            // the status code is logged.
            log.warn("abdm.consent.call.failed path[{}] status[{}] requestId[{}]",
                     path, e.getStatusCode().value(), requestId);
            throw new GovApiException("ABDM_" + e.getStatusCode().value(),
                                      "ABDM rejected the request", false);
        } catch (RuntimeException e) {
            log.error("abdm.consent.call.error path[{}] type[{}] requestId[{}]",
                      path, e.getClass().getSimpleName(), requestId);
            throw new GovApiException("ABDM_UNAVAILABLE", "ABDM is unreachable", true);
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException e) {
            // An unparseable date makes the record undated, and undated records
            // are excluded from a consented range rather than assumed in scope.
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new GovApiException("ABDM_RESPONSE_MALFORMED",
                                      "ABDM response did not contain " + field, false);
        }
        return node.get(field).asText();
    }
}
