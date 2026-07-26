package com.hms.infrastructure.gov;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Central bearer-token manager for the government gateways.
 *
 * <p>One component rather than a token fetch per client. ABDM and NHCX both issue
 * short-lived tokens, and scattering refresh logic means N places to get the
 * expiry skew wrong — with the failure showing up as intermittent 401s under
 * load, which is miserable to diagnose.
 *
 * <p>Refresh happens slightly before actual expiry: a token that is valid when
 * checked can expire in flight.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GovTokenManager {

    private final GovApiProperties properties;

    private final ReentrantLock abdmLock = new ReentrantLock();
    private volatile String abdmToken;
    private volatile Instant abdmExpiry = Instant.EPOCH;

    private final ReentrantLock nhcxLock = new ReentrantLock();
    private volatile String nhcxToken;
    private volatile Instant nhcxExpiry = Instant.EPOCH;

    /**
     * @throws GovApiException when credentials are absent, so a misconfigured
     *         deployment fails with a message naming the missing property rather
     *         than an opaque 401 from a government server.
     */
    public String abdmToken() {
        if (!properties.isAbdmConfigured()) {
            throw new GovApiException("ABDM_NOT_CONFIGURED",
                "ABDM credentials are not set. Provide hms.gov.abdm.base-url, client-id "
                + "and client-secret (env: HMS_GOV_ABDM_*).", false);
        }
        if (isFresh(abdmToken, abdmExpiry)) {
            return abdmToken;
        }
        abdmLock.lock();
        try {
            if (isFresh(abdmToken, abdmExpiry)) {
                return abdmToken;
            }
            GovApiProperties.Abdm cfg = properties.getAbdm();
            JsonNode body = post(cfg.getBaseUrl() + cfg.getSessionPath(),
                Map.of("clientId", cfg.getClientId(),
                       "clientSecret", cfg.getClientSecret(),
                       "grantType", "client_credentials"),
                cfg.getTimeoutSeconds(), "ABDM");

            abdmToken = text(body, "accessToken");
            long ttl = body.hasNonNull("expiresIn") ? body.get("expiresIn").asLong() : 900L;
            abdmExpiry = Instant.now().plusSeconds(
                Math.max(ttl - cfg.getTokenRefreshSkewSeconds(), 30));
            log.info("gov.token.refreshed provider[ABDM] ttlSeconds[{}]", ttl);
            return abdmToken;
        } finally {
            abdmLock.unlock();
        }
    }

    public String nhcxToken() {
        if (!properties.isNhcxConfigured()) {
            throw new GovApiException("NHCX_NOT_CONFIGURED",
                "NHCX credentials are not set. Provide hms.gov.nhcx.base-url, participant-code, "
                + "client-id and client-secret (env: HMS_GOV_NHCX_*).", false);
        }
        if (isFresh(nhcxToken, nhcxExpiry)) {
            return nhcxToken;
        }
        nhcxLock.lock();
        try {
            if (isFresh(nhcxToken, nhcxExpiry)) {
                return nhcxToken;
            }
            GovApiProperties.Nhcx cfg = properties.getNhcx();
            JsonNode body = post(cfg.getBaseUrl() + "/api/v1/auth/token",
                Map.of("client_id", cfg.getClientId(),
                       "client_secret", cfg.getClientSecret(),
                       "grant_type", "client_credentials"),
                cfg.getTimeoutSeconds(), "NHCX");

            nhcxToken = text(body, "access_token");
            long ttl = body.hasNonNull("expires_in") ? body.get("expires_in").asLong() : 900L;
            nhcxExpiry = Instant.now().plusSeconds(Math.max(ttl - 60, 30));
            log.info("gov.token.refreshed provider[NHCX] ttlSeconds[{}]", ttl);
            return nhcxToken;
        } finally {
            nhcxLock.unlock();
        }
    }

    /** Drop a cached token after a 401, so the next call re-authenticates. */
    public void invalidate(String provider) {
        if ("ABDM".equalsIgnoreCase(provider)) {
            abdmExpiry = Instant.EPOCH;
        } else {
            nhcxExpiry = Instant.EPOCH;
        }
        log.warn("gov.token.invalidated provider[{}]", provider);
    }

    private boolean isFresh(String token, Instant expiry) {
        return token != null && Instant.now().isBefore(expiry);
    }

    private JsonNode post(String url, Map<String, String> body, int timeoutSeconds,
                          String provider) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return RestClient.create()
                .post()
                .uri(url)
                .headers(h -> h.addAll(headers))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (RuntimeException e) {
            // The exception message can contain the request body, which holds the
            // client secret. Never let it reach a log or a response.
            log.error("gov.token.refresh.failed provider[{}] type[{}]",
                      provider, e.getClass().getSimpleName());
            throw new GovApiException(provider + "_AUTH_FAILED",
                "Could not obtain a " + provider + " access token", true);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new GovApiException("TOKEN_RESPONSE_MALFORMED",
                "Token response did not contain " + field, true);
        }
        return node.get(field).asText();
    }

    /** Unused-import guard for HttpEntity/Duration kept for readers extending this. */
    @SuppressWarnings("unused")
    private static void typeAnchor(HttpEntity<?> e, Duration d) {
    }
}
