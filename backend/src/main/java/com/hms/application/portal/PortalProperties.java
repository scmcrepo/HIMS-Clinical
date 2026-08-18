package com.hms.application.portal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Patient portal configuration.
 *
 * <p>Every default here is the conservative one. Where the requirement document
 * proposed a looser value (a 30-minute access token) the shorter one is used,
 * because a portal session reads clinical records and the cost of the tighter
 * setting is a background refresh call the patient never sees.
 */
@Component
@ConfigurationProperties(prefix = "hms.portal")
@Getter
@Setter
public class PortalProperties {

    /**
     * Whether a patient must prove possession of their mobile number via SMS
     * before any clinical data is returned.
     *
     * <p>Default true, and it should stay true in any environment holding real
     * patients. It exists so a developer can work without an SMS gateway, not
     * so the decision can be made quietly in a properties file — turning it off
     * logs a WARN naming the risk at startup and raises
     * {@code hms_portal_otp_disabled}.
     */
    private boolean otpRequired = true;

    /** Signing key for portal JWTs, base64, at least 32 bytes. No default: a working default is how a dev key reaches production. */
    private String jwtSecret = "";

    private String jwtIssuer = "hims-portal";

    /** Proves possession of a number. Reads no clinical data. Short because selection follows immediately. */
    private Duration identityTokenTtl = Duration.ofMinutes(10);

    /** WO-017 §9.2 — 15 minutes, not the requirement document's 30. */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    private Duration refreshTokenTtl = Duration.ofDays(7);

    private Duration otpTtl = Duration.ofMinutes(5);

    private int otpLength = 6;

    private short otpMaxAttempts = 5;

    /** Codes per number per window. */
    private int otpMaxPerNumber = 3;

    /** Codes per source per window — catches enumeration that rotates numbers. */
    private int otpMaxPerSource = 10;

    private Duration otpRateWindow = Duration.ofMinutes(10);

    /** Resend lockout, so Resend cannot be used to bypass the per-number cap. */
    private Duration otpResendCooldown = Duration.ofSeconds(30);

    /** Concurrent devices per patient. A third login revokes the oldest chain. */
    private int maxActiveDevices = 2;

    /** Self-registrations permitted per mobile number across all tenants. */
    private int maxSelfRegistrationsPerNumber = 3;

    /** Consent text version recorded against PORTAL_SELF_ACCESS. */
    private String consentVersion = "portal-self-access-v1";
}
