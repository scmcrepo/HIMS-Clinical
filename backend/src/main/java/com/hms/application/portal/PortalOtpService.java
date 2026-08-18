package com.hms.application.portal;

import com.hms.domain.shared.port.out.NotificationPort;
import com.hms.infrastructure.persistence.portal.PortalOtpChallengeEntity;
import com.hms.infrastructure.persistence.portal.PortalOtpChallengeJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Issues and verifies the SMS one-time codes that gate portal login.
 *
 * <p>WO-017 §4.0 explains why this exists at all when the requirement document
 * asked for number-only login: the portal returns diagnoses, lab results and
 * attachments, and a mobile number is an identifier rather than a secret.
 *
 * <p>Nothing in this class ever logs a mobile number or a code. The log carries
 * a truncated prefix of the HMAC token, which is enough to correlate one
 * patient's attempts across a support call and useless for identifying them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortalOtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Cost 10, not the 12 used for staff passwords: a 6-digit code lives 5 minutes and this is on the login path. */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private final PortalOtpChallengeJpaRepository challengeRepo;
    private final PiiSearchTokenService searchTokenService;
    private final NotificationPort notificationPort;
    private final PortalProperties properties;
    private final MeterRegistry meterRegistry;

    /** What the caller needs to drive the code-entry screen. Carries no hint about whether the number is known. */
    public record OtpChallengeIssued(
        java.util.UUID challengeId,
        long expiresInSeconds,
        long resendAvailableInSeconds) {}

    /**
     * Issues a code, or throws {@link PortalErrorCode#OTP_RATE_LIMITED}.
     *
     * <p>Runs identically whether or not the number matches a patient. That is
     * the point: an attacker who can tell "registered" from "not registered"
     * before verifying has already learned that this person is a patient at one
     * of these hospitals.
     *
     * @param rawMobile 10-digit number as typed
     * @param sourceHash salted hash of the caller's IP, or null
     */
    @Transactional
    public OtpChallengeIssued issue(String rawMobile, String sourceHash) {
        String token = searchTokenService.phoneToken(rawMobile);
        if (token == null) {
            // phoneToken returns null when the blind-index key is unconfigured.
            // Failing loudly beats issuing codes that can never be matched to a
            // patient, which would look like "no records found" for everyone.
            throw new PortalException(
                PortalErrorCode.VALIDATION_FAILED,
                "portal.otp.token_unavailable — is hms.security.search-token.key configured?");
        }

        Instant now = Instant.now();
        enforceRateLimits(token, sourceHash, now);

        String code = generateCode(properties.getOtpLength());

        PortalOtpChallengeEntity challenge = new PortalOtpChallengeEntity();
        challenge.setContactNumberToken(token);
        challenge.setCodeHash(ENCODER.encode(code));
        challenge.setAttempts((short) 0);
        challenge.setMaxAttempts(properties.getOtpMaxAttempts());
        challenge.setIssuedAt(now);
        challenge.setExpiresAt(now.plus(properties.getOtpTtl()));
        challenge.setSourceHash(sourceHash);
        PortalOtpChallengeEntity saved = challengeRepo.save(challenge);

        send(rawMobile, code);

        log.info("event=portal.otp.requested token_prefix={} challenge_id={}",
            prefix(token), saved.getId());
        meterRegistry.counter("hms_portal_otp_requests_total", "outcome", "issued").increment();

        return new OtpChallengeIssued(
            saved.getId(),
            properties.getOtpTtl().toSeconds(),
            properties.getOtpResendCooldown().toSeconds());
    }

    /**
     * Verifies a code and consumes the challenge.
     *
     * @return the HMAC token of the verified number, for the lookup that follows
     */
    @Transactional
    public String verify(java.util.UUID challengeId, String rawMobile, String code) {
        String token = searchTokenService.phoneToken(rawMobile);
        Instant now = Instant.now();

        Optional<PortalOtpChallengeEntity> found = challengeRepo.findById(challengeId);
        if (found.isEmpty()) {
            fail("OTP_INVALID");
            throw new PortalException(PortalErrorCode.OTP_INVALID, "portal.otp.unknown_challenge");
        }

        PortalOtpChallengeEntity challenge = found.get();

        // The challenge must belong to the number being claimed. Without this,
        // a caller could request a code to their own phone and then present its
        // challenge id alongside someone else's number.
        if (token == null || !token.equals(challenge.getContactNumberToken())) {
            fail("OTP_INVALID");
            throw new PortalException(PortalErrorCode.OTP_INVALID, "portal.otp.challenge_number_mismatch");
        }

        if (challenge.isConsumed()) {
            fail("OTP_INVALID");
            throw new PortalException(PortalErrorCode.OTP_INVALID, "portal.otp.already_consumed");
        }
        if (challenge.isExpired(now)) {
            fail("OTP_EXPIRED");
            throw new PortalException(PortalErrorCode.OTP_EXPIRED, "portal.otp.expired");
        }
        if (!challenge.hasAttemptsLeft()) {
            fail("OTP_ATTEMPTS_EXCEEDED");
            throw new PortalException(
                PortalErrorCode.OTP_ATTEMPTS_EXCEEDED, "portal.otp.attempts_exhausted");
        }

        // The attempt is recorded before the comparison, so a client that kills
        // the connection mid-request still burns a guess. Counting only on
        // failure would make the attempt cap bypassable by aborting.
        challenge.setAttempts((short) (challenge.getAttempts() + 1));
        challengeRepo.save(challenge);

        if (!ENCODER.matches(code, challenge.getCodeHash())) {
            fail("OTP_INVALID");
            log.info("event=portal.otp.verified outcome=invalid attempts={} token_prefix={}",
                challenge.getAttempts(), prefix(challenge.getContactNumberToken()));
            throw new PortalException(PortalErrorCode.OTP_INVALID, "portal.otp.code_mismatch");
        }

        challenge.setConsumedAt(now);
        challengeRepo.save(challenge);

        log.info("event=portal.otp.verified outcome=success attempts={} token_prefix={}",
            challenge.getAttempts(), prefix(challenge.getContactNumberToken()));
        meterRegistry.counter("hms_portal_otp_verify_total", "outcome", "success").increment();

        return challenge.getContactNumberToken();
    }

    /**
     * Development escape hatch for {@code hms.portal.otp.required=false}.
     *
     * <p>Returns the token without any verification. Guarded by the property and
     * by a startup WARN; see {@link PortalOtpStartupCheck}.
     */
    public String bypassVerification(String rawMobile) {
        if (properties.isOtpRequired()) {
            throw new IllegalStateException(
                "bypassVerification called while hms.portal.otp.required=true");
        }
        log.warn("event=portal.otp.bypassed — OTP IS DISABLED. Clinical records are reachable "
            + "with a mobile number alone. This must not be a production configuration.");
        meterRegistry.counter("hms_portal_otp_verify_total", "outcome", "bypassed").increment();
        return searchTokenService.phoneToken(rawMobile);
    }

    /** Retention: challenges are authentication artefacts, purged at 24h. */
    @Transactional
    public int purgeExpired() {
        int removed = challengeRepo.purgeExpiredBefore(Instant.now().minusSeconds(86_400));
        if (removed > 0) {
            log.info("event=portal.otp.purged count={}", removed);
        }
        return removed;
    }

    // ── internals ───────────────────────────────────────────────────────────

    private void enforceRateLimits(String token, String sourceHash, Instant now) {
        Instant windowStart = now.minus(properties.getOtpRateWindow());

        if (challengeRepo.countIssuedSince(token, windowStart) >= properties.getOtpMaxPerNumber()) {
            log.warn("event=portal.otp.rate_limited scope=number token_prefix={}", prefix(token));
            meterRegistry.counter("hms_portal_otp_requests_total", "outcome", "rate_limited_number")
                .increment();
            throw new PortalException(PortalErrorCode.OTP_RATE_LIMITED, "portal.otp.rate_limited_number");
        }

        if (sourceHash != null
                && challengeRepo.countIssuedBySourceSince(sourceHash, windowStart)
                    >= properties.getOtpMaxPerSource()) {
            // Catches the enumeration the per-number cap cannot see: one source
            // walking a block of numbers, one code each.
            log.warn("event=portal.otp.rate_limited scope=source");
            meterRegistry.counter("hms_portal_otp_requests_total", "outcome", "rate_limited_source")
                .increment();
            throw new PortalException(PortalErrorCode.OTP_RATE_LIMITED, "portal.otp.rate_limited_source");
        }
    }

    private void send(String rawMobile, String code) {
        try {
            notificationPort.sendSms(new NotificationPort.SmsMessage(
                rawMobile,
                "Your $hospital$ patient app code is $code$. It expires in $minutes$ minutes. "
                    + "Do not share it with anyone.",
                java.util.Map.of(
                    "code", code,
                    "hospital", "HIMS",
                    "minutes", String.valueOf(properties.getOtpTtl().toMinutes()))));
        } catch (RuntimeException e) {
            // The adapter is @Async and swallows its own failures, so this
            // catches only construction errors. Deliberately logs the exception
            // type and not the message: an SMS provider error body echoes the
            // destination number back.
            log.error("event=portal.otp.delivery_failed exception={}", e.getClass().getSimpleName());
            meterRegistry.counter("hms_portal_otp_requests_total", "outcome", "delivery_failed")
                .increment();
            throw new PortalException(PortalErrorCode.OTP_DELIVERY_FAILED, "portal.otp.send_failed");
        }
    }

    private void fail(String outcome) {
        meterRegistry.counter("hms_portal_otp_verify_total", "outcome", outcome.toLowerCase(java.util.Locale.ROOT))
            .increment();
    }

    /**
     * Uniformly distributed over the full range, including codes with leading
     * zeros. {@code RANDOM.nextInt(900000) + 100000} is the common shortcut and
     * it silently removes a tenth of the keyspace.
     */
    private static String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('0' + RANDOM.nextInt(10)));
        }
        return sb.toString();
    }

    /** First 8 characters of the token. Correlates attempts; identifies nobody. */
    private static String prefix(String token) {
        if (token == null) return "none";
        return token.length() <= 8 ? token : token.substring(0, 8);
    }

    /** Salted SHA-256 of a client address — enough to rate-limit, not enough to track. */
    public static String hashSource(String remoteAddress, String salt) {
        if (remoteAddress == null || remoteAddress.isBlank()) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(
                digest.digest(remoteAddress.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
