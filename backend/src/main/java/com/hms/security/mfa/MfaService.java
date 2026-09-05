package com.hms.security.mfa;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.mfa.MfaChallengeEntity;
import com.hms.infrastructure.persistence.mfa.MfaChallengeJpaRepository;
import com.hms.infrastructure.persistence.mfa.MfaRecoveryCodeEntity;
import com.hms.infrastructure.persistence.mfa.MfaRecoveryCodeJpaRepository;
import com.hms.infrastructure.persistence.mfa.UserMfaCredentialEntity;
import com.hms.infrastructure.persistence.mfa.UserMfaCredentialJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-factor authentication for privileged users — DPDP Rule 6, WO-029 / U-002.
 *
 * <h2>Why this was deferred, and how that shaped it</h2>
 * This was held back through five work orders for a stated reason: it rewires
 * the authentication path, which is the highest blast radius in the system, and
 * it cannot be verified in the environment the campaign runs in. That reasoning
 * has not changed, so the design is built around it.
 *
 * <p>The mode defaults to {@link Mode#OFF}, in which every method here is a
 * no-op and the login path behaves exactly as it did before. Nothing about this
 * feature can lock anyone out until an administrator deliberately turns it on.
 * An unverified change that is inert by default is a much smaller bet than an
 * unverified change that is live on deploy.
 *
 * <h2>The rollout order is not optional</h2>
 * <ol>
 *   <li>{@code OFF} — shipped state. Enrolment endpoints refuse.</li>
 *   <li>{@code OPTIONAL} — users may enrol; those who have are challenged, those
 *       who have not are let through. Watch {@code hms_mfa_privileged_uncovered}
 *       fall to zero.</li>
 *   <li>{@code REQUIRED} — an unenrolled privileged user cannot log in.</li>
 * </ol>
 *
 * <p>Switching straight from OFF to REQUIRED locks out every privileged user
 * simultaneously, including the ones who would have to fix it. The gauge exists
 * so that decision can be made on a number rather than on optimism.
 *
 * <h2>What counts as privileged</h2>
 * Configured by role name, defaulting to SUPERADMIN and HOSPITAL_ADMIN. Those
 * are the accounts that can read across an entire hospital, which is the
 * exposure Rule 6 is asking about. It is a list rather than a hardcoded pair
 * because which roles are sensitive is a per-deployment fact.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    public enum Mode { OFF, OPTIONAL, REQUIRED }

    /** How long a user has to enter their code after the password step. */
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

    /** Wrong codes allowed against one challenge before it is burned. */
    private static final int MAX_CHALLENGE_ATTEMPTS = 5;

    /** Consecutive wrong codes before the credential itself is locked. */
    private static final int MAX_CREDENTIAL_FAILURES = 10;

    private static final Duration CREDENTIAL_LOCKOUT = Duration.ofMinutes(15);

    private static final int RECOVERY_CODE_COUNT = 10;

    /** Unambiguous alphabet: no O/0, no I/1/L. These get read aloud and retyped. */
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMfaCredentialJpaRepository credentials;
    private final MfaRecoveryCodeJpaRepository recoveryCodes;
    private final MfaChallengeJpaRepository challenges;
    private final PasswordEncoder passwordEncoder;
    private final MeterRegistry meterRegistry;

    @Value("${hms.mfa.mode:OFF}")
    private String configuredMode;

    @Value("${hms.mfa.privileged-roles:SUPERADMIN,HOSPITAL_ADMIN}")
    private String privilegedRoles;

    @Value("${hms.mfa.issuer:HIMS Clinical}")
    private String issuer;

    public Mode mode() {
        try {
            return Mode.valueOf(configuredMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // A typo in a property must not silently become an enforcement
            // decision in either direction. OFF is the safe reading: it cannot
            // lock anyone out, and the log line says why the setting was ignored.
            log.error("event=mfa.mode.invalid value={} falling_back=OFF", configuredMode);
            return Mode.OFF;
        }
    }

    public boolean isPrivileged(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return false;
        }
        for (String role : privilegedRoles.split(",")) {
            if (roleNames.contains(role.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the login path should do for this user, having accepted the password.
     *
     * <p>The whole policy lives in this one method so that {@code AuthController}
     * asks a question rather than reimplements a decision. Only the OFF branch
     * short-circuits: in every other mode the answer depends on whether this
     * particular user has finished enrolling.
     */
    @Transactional(readOnly = true)
    public Decision decide(UUID userId, Set<String> roleNames) {
        Mode mode = mode();
        if (mode == Mode.OFF) {
            return Decision.PROCEED;
        }

        boolean enrolled = credentials.findByUserId(userId)
                                      .filter(UserMfaCredentialEntity::isConfirmed)
                                      .isPresent();
        if (enrolled) {
            return Decision.CHALLENGE;
        }
        if (mode == Mode.REQUIRED && isPrivileged(roleNames)) {
            return Decision.ENROLMENT_REQUIRED;
        }
        return Decision.PROCEED;
    }

    public enum Decision {
        /** No second factor applies. Complete the login as before. */
        PROCEED,
        /** Raise a challenge and wait for a code. */
        CHALLENGE,
        /** Privileged, unenrolled, and the mode is REQUIRED. Refuse. */
        ENROLMENT_REQUIRED
    }

    // ── Enrolment ───────────────────────────────────────────────────────────

    /**
     * Start enrolment, returning the secret and the provisioning URI.
     *
     * <p>Re-enrolling replaces an unconfirmed credential outright rather than
     * erroring: someone who scanned a QR code, lost the tab and came back should
     * get a working one, not a support ticket. A CONFIRMED credential is not
     * replaced — changing a working second factor without proving control of the
     * old one is the obvious way to steal an account, and that path is
     * {@link #resetFor} with its own permission.
     */
    @Transactional
    public Enrolment beginEnrolment(UUID userId, UUID tenantId, String username) {
        if (mode() == Mode.OFF) {
            throw new BusinessRuleViolationException(
                "Multi-factor authentication is switched off for this deployment. "
                + "Enrolling now would produce a credential nothing checks");
        }

        UserMfaCredentialEntity existing = credentials.findByUserId(userId).orElse(null);
        if (existing != null && existing.isConfirmed()) {
            throw new BusinessRuleViolationException(
                "This account already has multi-factor authentication set up. To "
                + "move it to a new device, an administrator must reset it first");
        }

        UserMfaCredentialEntity credential = existing != null
            ? existing
            : new UserMfaCredentialEntity();

        String secret = TotpGenerator.generateSecret();
        credential.setUserId(userId);
        credential.setTenantId(tenantId);
        credential.setSecret(secret);
        credential.setConfirmedAt(null);
        credential.setLastTimeStep(null);
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        UserMfaCredentialEntity saved = credentials.save(credential);

        log.info("event=mfa.enrolment.started user_id={}", userId);
        return new Enrolment(saved.getId(), secret,
                             TotpGenerator.provisioningUri(issuer, username, secret));
    }

    /**
     * Finish enrolment by proving a code can be generated, and issue recovery
     * codes.
     *
     * <p>The recovery codes are returned here and nowhere else. They are the
     * answer to "my phone is in the sea", and the alternative to having one is
     * an administrator reset, which is a slower and more forgeable path.
     */
    @Transactional
    public List<String> confirmEnrolment(UUID userId, String code) {
        UserMfaCredentialEntity credential = credentials.findByUserId(userId)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "Enrolment has not been started for this account"));

        if (credential.isConfirmed()) {
            throw new BusinessRuleViolationException(
                "Multi-factor authentication is already set up for this account");
        }

        long step = TotpGenerator.timeStep(Instant.now().getEpochSecond());
        Long matched = TotpGenerator.matchingStep(credential.getSecret(), code, step);
        if (matched == null) {
            meterRegistry.counter("hms_mfa_enrolment_failures_total").increment();
            throw new BusinessRuleViolationException(
                "That code is not correct. Check the time on the device running "
                + "your authenticator app — TOTP codes depend on the clock");
        }

        credential.setConfirmedAt(Instant.now());
        credential.setLastTimeStep(matched);
        credentials.save(credential);

        List<String> plaintext = generateRecoveryCodes(credential.getId());

        meterRegistry.counter("hms_mfa_enrolments_total").increment();
        log.info("event=mfa.enrolment.confirmed user_id={} recovery_codes={}",
                 userId, plaintext.size());
        return plaintext;
    }

    private List<String> generateRecoveryCodes(UUID credentialId) {
        recoveryCodes.deleteAllForCredential(credentialId);

        List<String> plaintext = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomRecoveryCode();
            plaintext.add(code);

            MfaRecoveryCodeEntity entity = new MfaRecoveryCodeEntity();
            entity.setCredentialId(credentialId);
            entity.setCodeHash(passwordEncoder.encode(code));
            recoveryCodes.save(entity);
        }
        return plaintext;
    }

    private String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    // ── Login challenge ─────────────────────────────────────────────────────

    @Transactional
    public MfaChallengeEntity raiseChallenge(UUID userId, UUID branchId, boolean forceLogout) {
        MfaChallengeEntity challenge = new MfaChallengeEntity();
        challenge.setUserId(userId);
        challenge.setBranchId(branchId);
        challenge.setForceLogout(forceLogout);
        challenge.setExpiresAt(Instant.now().plus(CHALLENGE_TTL));

        MfaChallengeEntity saved = challenges.save(challenge);
        meterRegistry.counter("hms_mfa_challenges_total").increment();
        log.info("event=mfa.challenge.raised user_id={} challenge_id={}", userId, saved.getId());
        return saved;
    }

    /**
     * Verify a code or a recovery code against a challenge.
     *
     * <p>Accepts either. A user whose phone is lost has a recovery code and
     * nothing else, and refusing it here would mean the only way back in is an
     * administrator with {@code MFA_ADMIN} — which is a worse security position,
     * because it makes that reset path routine rather than exceptional.
     *
     * @return the consumed challenge, which carries the branch the user chose
     *         in the first step
     */
    @Transactional
    public MfaChallengeEntity verifyChallenge(UUID challengeId, String code) {
        MfaChallengeEntity challenge = challenges.findById(challengeId)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "This sign-in attempt is no longer valid. Please start again"));

        if (!challenge.isUsable()) {
            throw new BusinessRuleViolationException(
                "This sign-in attempt has expired. Please start again");
        }

        UserMfaCredentialEntity credential = credentials.findByUserId(challenge.getUserId())
            .filter(UserMfaCredentialEntity::isConfirmed)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "No multi-factor credential is set up for this account"));

        if (credential.isLocked()) {
            throw new BusinessRuleViolationException(
                "Too many incorrect codes. Try again in a few minutes");
        }

        challenge.setAttempts(challenge.getAttempts() + 1);
        if (challenge.getAttempts() > MAX_CHALLENGE_ATTEMPTS) {
            challenge.setConsumedAt(Instant.now());
            challenges.save(challenge);
            throw new BusinessRuleViolationException(
                "Too many incorrect codes for this sign-in attempt. Please start again");
        }
        challenges.save(challenge);

        long currentStep = TotpGenerator.timeStep(Instant.now().getEpochSecond());
        Long matched = TotpGenerator.matchingStep(credential.getSecret(), code, currentStep);

        if (matched != null) {
            // Replay guard. A code observed over a shoulder or captured in a
            // proxy stays arithmetically valid for the rest of its window; this
            // is what stops it being usable a second time.
            if (credential.getLastTimeStep() != null && matched <= credential.getLastTimeStep()) {
                meterRegistry.counter("hms_mfa_replays_rejected_total").increment();
                log.warn("event=mfa.replay.rejected user_id={} step={}",
                         challenge.getUserId(), matched);
                recordFailure(credential);
                throw new BusinessRuleViolationException(
                    "That code has already been used. Wait for your authenticator "
                    + "app to show the next one");
            }
            credential.setLastTimeStep(matched);
            return succeed(credential, challenge, "TOTP");
        }

        Optional<MfaRecoveryCodeEntity> recovery = matchRecoveryCode(credential.getId(), code);
        if (recovery.isPresent()) {
            MfaRecoveryCodeEntity used = recovery.get();
            used.setUsedAt(Instant.now());
            recoveryCodes.save(used);

            long remaining = recoveryCodes.countByCredentialIdAndUsedAtIsNull(credential.getId());
            meterRegistry.counter("hms_mfa_recovery_code_uses_total").increment();
            log.warn("event=mfa.recovery_code.used user_id={} remaining={}",
                     challenge.getUserId(), remaining);
            return succeed(credential, challenge, "RECOVERY_CODE");
        }

        recordFailure(credential);
        meterRegistry.counter("hms_mfa_verification_failures_total").increment();
        throw new BusinessRuleViolationException("That code is not correct");
    }

    /**
     * BCrypt every unused code and compare.
     *
     * <p>Deliberately does not short-circuit on the first match, so the work
     * done is the same whether the code is right, wrong, or right-but-already-used.
     * Ten hashes is a few hundred milliseconds, which is acceptable on a path
     * taken only when someone has lost their phone.
     */
    private Optional<MfaRecoveryCodeEntity> matchRecoveryCode(UUID credentialId, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalised = code.trim().toUpperCase(Locale.ROOT);

        MfaRecoveryCodeEntity found = null;
        for (MfaRecoveryCodeEntity candidate : recoveryCodes.findByCredentialIdAndUsedAtIsNull(credentialId)) {
            if (passwordEncoder.matches(normalised, candidate.getCodeHash()) && found == null) {
                found = candidate;
            }
        }
        return Optional.ofNullable(found);
    }

    private MfaChallengeEntity succeed(UserMfaCredentialEntity credential,
                                       MfaChallengeEntity challenge, String method) {
        credential.setFailedAttempts(0);
        credential.setLockedUntil(null);
        credentials.save(credential);

        challenge.setConsumedAt(Instant.now());
        challenges.save(challenge);

        meterRegistry.counter("hms_mfa_verifications_total", "method", method).increment();
        log.info("event=mfa.verified user_id={} method={}", challenge.getUserId(), method);
        return challenge;
    }

    private void recordFailure(UserMfaCredentialEntity credential) {
        credential.setFailedAttempts(credential.getFailedAttempts() + 1);
        if (credential.getFailedAttempts() >= MAX_CREDENTIAL_FAILURES) {
            credential.setLockedUntil(Instant.now().plus(CREDENTIAL_LOCKOUT));
            credential.setFailedAttempts(0);
            log.warn("event=mfa.credential.locked user_id={} minutes={}",
                     credential.getUserId(), CREDENTIAL_LOCKOUT.toMinutes());
        }
        credentials.save(credential);
    }

    // ── Administration ──────────────────────────────────────────────────────

    /**
     * Clear another user's second factor.
     *
     * <p>The break-glass path, for a user who has lost both their device and
     * their recovery codes. It is a real weakening — after this the account is
     * password-only until it re-enrols — so it is permissioned separately and
     * logged at WARN with the actor named. That log line is the control; the
     * endpoint cannot be made safe by argument alone.
     */
    @Transactional
    public void resetFor(UUID userId, UUID actorId) {
        credentials.findByUserId(userId).ifPresent(credential -> {
            recoveryCodes.deleteAllForCredential(credential.getId());
            credentials.delete(credential);
            meterRegistry.counter("hms_mfa_admin_resets_total").increment();
            log.warn("event=mfa.admin_reset user_id={} actor_id={}", userId, actorId);
        });
    }

    @Transactional(readOnly = true)
    public Status statusFor(UUID userId) {
        return credentials.findByUserId(userId)
            .map(c -> new Status(true, c.isConfirmed(),
                                 c.isConfirmed()
                                     ? recoveryCodes.countByCredentialIdAndUsedAtIsNull(c.getId())
                                     : 0))
            .orElseGet(() -> new Status(false, false, 0));
    }

    /** How many of the given privileged users have no confirmed credential. */
    @Transactional(readOnly = true)
    public long uncoveredCount(List<UUID> privilegedUserIds) {
        if (privilegedUserIds == null || privilegedUserIds.isEmpty()) {
            return 0;
        }
        return privilegedUserIds.size() - credentials.confirmedUserIdsIn(privilegedUserIds).size();
    }

    public record Enrolment(UUID credentialId, String secret, String provisioningUri) {}

    public record Status(boolean enrolled, boolean confirmed, long recoveryCodesRemaining) {}
}
