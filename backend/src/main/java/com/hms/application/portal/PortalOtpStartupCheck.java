package com.hms.application.portal;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Makes a disabled OTP requirement impossible to miss.
 *
 * <p>{@code hms.portal.otp.required=false} is a legitimate developer setting and
 * a serious production misconfiguration, and the two are indistinguishable from
 * inside the process. So rather than refusing to start — which would make a
 * local checkout hostile — this logs a warning naming the actual consequence and
 * publishes a gauge that a dashboard can alert on. WO-017 §6 alerts when the
 * gauge sits at 1 for more than an hour.
 *
 * <p>The signing-key check is separate and is fatal: a blank JWT secret is never
 * a valid configuration, and starting with one would mint portal tokens anybody
 * could forge.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PortalOtpStartupCheck {

    private static final int MIN_SECRET_BYTES = 32;

    private final PortalProperties properties;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger otpDisabledGauge = new AtomicInteger(0);

    @PostConstruct
    void check() {
        meterRegistry.gauge("hms_portal_otp_disabled", otpDisabledGauge);

        if (!properties.isOtpRequired()) {
            otpDisabledGauge.set(1);
            log.warn("""

                ============================================================
                 PATIENT PORTAL OTP IS DISABLED (hms.portal.otp.required=false)

                 Any caller who knows a patient's mobile number can read that
                 patient's diagnoses, lab results and attachments — at every
                 hospital on this platform, because the portal lookup spans
                 tenants. Mobile numbers are not secrets.

                 This is a development-only setting.
                ============================================================""");
        } else {
            otpDisabledGauge.set(0);
        }

        String secret = properties.getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "hms.portal.jwt-secret is not configured. Portal tokens cannot be signed. "
                + "Generate one with: openssl rand -base64 32");
        }
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("hms.portal.jwt-secret is not valid base64", e);
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            // HS256 with a short key is forgeable by brute force, and a signing
            // key that fails silently is worse than no signature at all.
            throw new IllegalStateException(
                "hms.portal.jwt-secret must decode to at least " + MIN_SECRET_BYTES
                + " bytes (got " + decoded.length + ")");
        }
    }
}
