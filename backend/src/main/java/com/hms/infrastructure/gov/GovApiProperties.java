package com.hms.infrastructure.gov;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Credentials and endpoints for the national health APIs.
 *
 * <p>Everything here is externalised. Nothing has a working default, and that is
 * deliberate: a default that happens to point at a sandbox is how test
 * credentials reach production. Supply via environment variables —
 * {@code HMS_ABDM_CLIENT_ID}, {@code HMS_ABDM_CLIENT_SECRET},
 * {@code HMS_NHCX_*} — or a secrets manager. Never commit them.
 *
 * <p>{@link #isAbdmConfigured()} and {@link #isNhcxConfigured()} let callers fail
 * loudly and specifically when a deployment has not been given credentials,
 * rather than producing a confusing 401 from a government gateway.
 */
@Component
@ConfigurationProperties(prefix = "hms.gov")
@Getter
@Setter
public class GovApiProperties {

    private Abdm abdm = new Abdm();
    private Nhcx nhcx = new Nhcx();

    @Getter
    @Setter
    public static class Abdm {
        /** Sandbox: https://dev.abdm.gov.in — production differs. */
        private String baseUrl = "";
        private String sessionPath = "/api/hiecm/gateway/v3/sessions";
        private String clientId = "";
        private String clientSecret = "";
        /** Health Facility Registry id for this hospital. */
        private String facilityId = "";
        private int timeoutSeconds = 15;
        /** Refresh this many seconds before the token actually expires. */
        private int tokenRefreshSkewSeconds = 60;
    }

    @Getter
    @Setter
    public static class Nhcx {
        private String baseUrl = "";
        private String participantCode = "";
        private String clientId = "";
        private String clientSecret = "";
        /**
         * Path to the provider's signing key. NHCX payloads are signed and
         * encrypted between participants; this is a secrets-manager reference,
         * not a value to put in application.yml.
         */
        private String signingKeyRef = "";
        private String encryptionKeyRef = "";
        /** Where the payer posts its asynchronous response. */
        private String callbackUrl = "";
        private int timeoutSeconds = 30;
        /** How long to wait for a callback before escalating to a human. */
        private int callbackTimeoutMinutes = 120;
    }

    public boolean isAbdmConfigured() {
        return notBlank(abdm.baseUrl) && notBlank(abdm.clientId) && notBlank(abdm.clientSecret);
    }

    public boolean isNhcxConfigured() {
        return notBlank(nhcx.baseUrl) && notBlank(nhcx.participantCode)
            && notBlank(nhcx.clientId) && notBlank(nhcx.clientSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
