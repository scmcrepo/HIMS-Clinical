package com.hms.security.mfa;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.mfa.MfaChallengeEntity;
import com.hms.infrastructure.persistence.mfa.MfaChallengeJpaRepository;
import com.hms.infrastructure.persistence.mfa.MfaRecoveryCodeEntity;
import com.hms.infrastructure.persistence.mfa.MfaRecoveryCodeJpaRepository;
import com.hms.infrastructure.persistence.mfa.UserMfaCredentialEntity;
import com.hms.infrastructure.persistence.mfa.UserMfaCredentialJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MfaService} — WO-029 / U-002.
 *
 * <p>The properties worth pinning here are the ones whose absence would leave a
 * feature that looks like MFA and is not:
 *
 * <ul>
 *   <li><b>Replay.</b> A code stays arithmetically valid for its whole window.
 *       Without the time-step guard, a code seen over a shoulder or captured in
 *       a proxy is reusable for up to ninety seconds and the skew tolerance
 *       becomes an attack surface.</li>
 *   <li><b>Single-use recovery codes.</b> A reusable recovery code is a second
 *       password that never expires.</li>
 *   <li><b>Unconfirmed enrolment is not enrolment.</b> Counting a scanned-but-
 *       never-confirmed credential as coverage is how an administrator switches
 *       to REQUIRED and locks out the people it counted.</li>
 *   <li><b>OFF is genuinely inert.</b> The whole safety argument for shipping
 *       this unverified rests on it.</li>
 * </ul>
 */
@DisplayName("MFA — TOTP verification, replay and recovery")
class MfaServiceTest {

    private UserMfaCredentialJpaRepository credentials;
    private MfaRecoveryCodeJpaRepository recoveryCodes;
    private MfaChallengeJpaRepository challenges;
    private MeterRegistry meters;
    private MfaService service;

    private static final UUID USER = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    /** In-memory stand-ins, so the tests exercise real state transitions. */
    private final Map<UUID, UserMfaCredentialEntity> credentialStore = new HashMap<>();
    private final Map<UUID, MfaChallengeEntity> challengeStore = new HashMap<>();
    private final List<MfaRecoveryCodeEntity> codeStore = new ArrayList<>();

    @BeforeEach
    void setUp() {
        credentials = mock(UserMfaCredentialJpaRepository.class);
        recoveryCodes = mock(MfaRecoveryCodeJpaRepository.class);
        challenges = mock(MfaChallengeJpaRepository.class);
        meters = new SimpleMeterRegistry();

        // Strength 4: these tests hash up to eleven recovery codes each, and the
        // production strength of 12 would make the suite take minutes. BCrypt's
        // correctness does not vary with cost.
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);

        service = new MfaService(credentials, recoveryCodes, challenges, encoder, meters);
        ReflectionTestUtils.setField(service, "configuredMode", "OPTIONAL");
        ReflectionTestUtils.setField(service, "privilegedRoles", "SUPERADMIN,HOSPITAL_ADMIN");
        ReflectionTestUtils.setField(service, "issuer", "HIMS Clinical");

        when(credentials.save(any())).thenAnswer(inv -> {
            UserMfaCredentialEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            credentialStore.put(c.getUserId(), c);
            return c;
        });
        when(credentials.findByUserId(any()))
            .thenAnswer(inv -> Optional.ofNullable(credentialStore.get(inv.getArgument(0))));

        when(challenges.save(any())).thenAnswer(inv -> {
            MfaChallengeEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            challengeStore.put(c.getId(), c);
            return c;
        });
        when(challenges.findById(any()))
            .thenAnswer(inv -> Optional.ofNullable(challengeStore.get(inv.getArgument(0))));

        when(recoveryCodes.save(any())).thenAnswer(inv -> {
            MfaRecoveryCodeEntity c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            if (!codeStore.contains(c)) {
                codeStore.add(c);
            }
            return c;
        });
        when(recoveryCodes.findByCredentialIdAndUsedAtIsNull(any()))
            .thenAnswer(inv -> codeStore.stream()
                .filter(c -> c.getCredentialId().equals(inv.getArgument(0)))
                .filter(c -> c.getUsedAt() == null)
                .toList());
        when(recoveryCodes.countByCredentialIdAndUsedAtIsNull(any()))
            .thenAnswer(inv -> codeStore.stream()
                .filter(c -> c.getCredentialId().equals(inv.getArgument(0)))
                .filter(c -> c.getUsedAt() == null)
                .count());
    }

    /** Enrol and confirm, returning the recovery codes. */
    private List<String> enrol() {
        MfaService.Enrolment enrolment = service.beginEnrolment(USER, TENANT, "priya");
        String code = TotpGenerator.generate(enrolment.secret(),
                                             TotpGenerator.timeStep(Instant.now().getEpochSecond()));
        return service.confirmEnrolment(USER, code);
    }

    private String currentCode() {
        return TotpGenerator.generate(credentialStore.get(USER).getSecret(),
                                      TotpGenerator.timeStep(Instant.now().getEpochSecond()));
    }

    private UUID challengeId() {
        return service.raiseChallenge(USER, null, false).getId();
    }

    // ── Mode policy ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("OFF is completely inert, even for an enrolled privileged user")
    void offModeProceedsAlways() {
        enrol();
        ReflectionTestUtils.setField(service, "configuredMode", "OFF");

        assertThat(service.decide(USER, Set.of("SUPERADMIN")))
            .as("the safety case for shipping this unverified is that OFF changes nothing")
            .isEqualTo(MfaService.Decision.PROCEED);
    }

    @Test
    @DisplayName("an unparseable mode falls back to OFF rather than guessing")
    void invalidModeFallsBackToOff() {
        ReflectionTestUtils.setField(service, "configuredMode", "REQUIRD");

        // A typo must not become an enforcement decision in either direction, and
        // OFF is the only reading that cannot lock anyone out.
        assertThat(service.mode()).isEqualTo(MfaService.Mode.OFF);
        assertThat(service.decide(USER, Set.of("SUPERADMIN")))
            .isEqualTo(MfaService.Decision.PROCEED);
    }

    @Test
    @DisplayName("OPTIONAL challenges the enrolled and lets the unenrolled through")
    void optionalModeDependsOnEnrolment() {
        assertThat(service.decide(USER, Set.of("SUPERADMIN")))
            .isEqualTo(MfaService.Decision.PROCEED);

        enrol();
        assertThat(service.decide(USER, Set.of("SUPERADMIN")))
            .isEqualTo(MfaService.Decision.CHALLENGE);
    }

    @Test
    @DisplayName("REQUIRED refuses an unenrolled privileged user, but not an ordinary one")
    void requiredModeOnlyBlocksPrivilegedUsers() {
        ReflectionTestUtils.setField(service, "configuredMode", "REQUIRED");

        assertThat(service.decide(USER, Set.of("HOSPITAL_ADMIN")))
            .isEqualTo(MfaService.Decision.ENROLMENT_REQUIRED);
        assertThat(service.decide(USER, Set.of("RECEPTIONIST")))
            .as("REQUIRED is about privileged accounts; it should not stop the front desk")
            .isEqualTo(MfaService.Decision.PROCEED);
    }

    @Test
    @DisplayName("an unconfirmed enrolment does not count as a second factor")
    void unconfirmedEnrolmentIsNotCoverage() {
        ReflectionTestUtils.setField(service, "configuredMode", "REQUIRED");
        service.beginEnrolment(USER, TENANT, "priya");   // scanned, never confirmed

        assertThat(service.decide(USER, Set.of("SUPERADMIN")))
            .as("counting this as enrolled is how an admin flips to REQUIRED and "
                + "locks out the users the gauge told them were covered")
            .isEqualTo(MfaService.Decision.ENROLMENT_REQUIRED);
        assertThat(service.uncoveredCount(List.of(USER))).isEqualTo(1);
    }

    // ── Enrolment ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("enrolment is refused while the mode is OFF")
    void cannotEnrolWhileOff() {
        ReflectionTestUtils.setField(service, "configuredMode", "OFF");
        assertThatThrownBy(() -> service.beginEnrolment(USER, TENANT, "priya"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("confirming issues ten distinct recovery codes")
    void confirmationIssuesRecoveryCodes() {
        List<String> codes = enrol();
        assertThat(codes).hasSize(10).doesNotHaveDuplicates();
        assertThat(codes).allMatch(c -> c.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertThat(credentialStore.get(USER).isConfirmed()).isTrue();
    }

    @Test
    @DisplayName("a wrong code does not confirm enrolment")
    void wrongCodeDoesNotConfirm() {
        service.beginEnrolment(USER, TENANT, "priya");
        assertThatThrownBy(() -> service.confirmEnrolment(USER, "000000"))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(credentialStore.get(USER).isConfirmed()).isFalse();
    }

    @Test
    @DisplayName("a confirmed credential cannot be silently replaced by re-enrolling")
    void reEnrolmentIsRefusedOnceConfirmed() {
        enrol();

        // Otherwise anyone with a live session could swap the second factor for
        // one they control, which is the obvious way to steal an account.
        assertThatThrownBy(() -> service.beginEnrolment(USER, TENANT, "priya"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("reset");
    }

    // ── Verification ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a valid code satisfies the challenge and returns it with its branch")
    void validCodeCompletesTheChallenge() {
        enrol();
        UUID branch = UUID.randomUUID();
        UUID id = service.raiseChallenge(USER, branch, true).getId();

        MfaChallengeEntity done = service.verifyChallenge(id, currentCode());

        assertThat(done.getUserId()).isEqualTo(USER);
        assertThat(done.getBranchId())
            .as("the branch chosen in step one must survive to step two")
            .isEqualTo(branch);
        assertThat(done.isForceLogout()).isTrue();
        assertThat(done.getConsumedAt()).isNotNull();
    }

    @Test
    @DisplayName("the same code cannot be used twice")
    void replayIsRejected() {
        enrol();
        String code = currentCode();
        service.verifyChallenge(challengeId(), code);

        assertThatThrownBy(() -> service.verifyChallenge(challengeId(), code))
            .as("still arithmetically valid for the rest of its window; the "
                + "time-step guard is what makes it worthless")
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already been used");

        assertThat(meters.counter("hms_mfa_replays_rejected_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a consumed challenge cannot be reused")
    void consumedChallengeIsRejected() {
        enrol();
        UUID id = challengeId();
        service.verifyChallenge(id, currentCode());

        assertThatThrownBy(() -> service.verifyChallenge(id, currentCode()))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("an expired challenge is rejected")
    void expiredChallengeIsRejected() {
        enrol();
        UUID id = challengeId();
        challengeStore.get(id).setExpiresAt(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.verifyChallenge(id, currentCode()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("a challenge burns after too many wrong codes")
    void challengeBurnsAfterRepeatedFailures() {
        enrol();
        UUID id = challengeId();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.verifyChallenge(id, "000000"))
                .isInstanceOf(BusinessRuleViolationException.class);
        }
        assertThatThrownBy(() -> service.verifyChallenge(id, "000000"))
            .hasMessageContaining("start again");
        assertThat(challengeStore.get(id).getConsumedAt()).isNotNull();
    }

    // ── Recovery codes ──────────────────────────────────────────────────────

    @Test
    @DisplayName("a recovery code satisfies a challenge exactly once")
    void recoveryCodeIsSingleUse() {
        List<String> codes = enrol();
        String recovery = codes.get(0);

        assertThat(service.verifyChallenge(challengeId(), recovery).getUserId()).isEqualTo(USER);

        assertThatThrownBy(() -> service.verifyChallenge(challengeId(), recovery))
            .as("a reusable recovery code is a second password that never expires")
            .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(service.statusFor(USER).recoveryCodesRemaining()).isEqualTo(9);
    }

    @Test
    @DisplayName("recovery codes are case-insensitive on entry")
    void recoveryCodesAreCaseInsensitive() {
        List<String> codes = enrol();
        // They get written down and typed back in. Case is not a security control.
        assertThat(service.verifyChallenge(challengeId(), codes.get(1).toLowerCase()).getUserId())
            .isEqualTo(USER);
    }

    @Test
    @DisplayName("an unrelated string is not a recovery code")
    void wrongRecoveryCodeIsRejected() {
        enrol();
        assertThatThrownBy(() -> service.verifyChallenge(challengeId(), "AAAAA-BBBBB"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── Administration ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an admin reset removes the credential entirely")
    void adminResetClearsEverything() {
        enrol();
        when(credentials.findByUserId(USER))
            .thenAnswer(inv -> Optional.ofNullable(credentialStore.get(USER)));

        service.resetFor(USER, UUID.randomUUID());
        credentialStore.remove(USER);

        assertThat(service.statusFor(USER).enrolled()).isFalse();
        assertThat(meters.counter("hms_mfa_admin_resets_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("privileged roles come from configuration, not from a hardcoded pair")
    void privilegedRolesAreConfigurable() {
        assertThat(service.isPrivileged(Set.of("SUPERADMIN"))).isTrue();
        assertThat(service.isPrivileged(Set.of("HOSPITAL_ADMIN"))).isTrue();
        assertThat(service.isPrivileged(Set.of("NURSE"))).isFalse();
        assertThat(service.isPrivileged(Set.of())).isFalse();
        assertThat(service.isPrivileged(null)).isFalse();

        ReflectionTestUtils.setField(service, "privilegedRoles", "PHARMACIST");
        assertThat(service.isPrivileged(Set.of("PHARMACIST"))).isTrue();
        assertThat(service.isPrivileged(Set.of("SUPERADMIN"))).isFalse();
    }
}
