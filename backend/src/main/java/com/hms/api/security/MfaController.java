package com.hms.api.security;

import com.hms.api.shared.ApiResponse;
import com.hms.security.HmsUserDetails;
import com.hms.security.mfa.MfaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Enrolling, inspecting and resetting a second factor (WO-029 / U-002).
 *
 * <p>Split from {@code AuthController} because everything here requires an
 * authenticated session, while the login and verify steps by definition do not.
 * Keeping the unauthenticated surface as small as possible is worth a second
 * file.
 *
 * <h2>Enrolment is self-service, reset is not</h2>
 * A user may always set up their own second factor: no feature key guards
 * {@code /enrol}, because requiring a permission to improve the security of your
 * own account would be an obstacle with no upside. Clearing <em>someone else's</em>
 * second factor is different — it leaves that account password-only until they
 * re-enrol — so it sits behind {@code MFA_ADMIN} and is logged at WARN with the
 * actor named.
 */
@RestController
@RequestMapping("/security/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    /**
     * Whether this account has a second factor, and how many recovery codes are
     * left.
     *
     * <p>Never returns the secret. After enrolment the secret exists only in the
     * user's authenticator app and in the encrypted column; there is no endpoint
     * that can show it again, which is why losing the device means recovery
     * codes or an administrator.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<MfaStatusView>> status(
            @AuthenticationPrincipal HmsUserDetails user) {

        MfaService.Status status = mfaService.statusFor(user.getId());
        return ResponseEntity.ok(ApiResponse.ok("OK", new MfaStatusView(
            mfaService.mode().name(),
            status.enrolled(),
            status.confirmed(),
            status.recoveryCodesRemaining(),
            mfaService.isPrivileged(user.getRoleNames()))));
    }

    /**
     * Begin enrolment: returns the secret and an {@code otpauth://} URI to render
     * as a QR code.
     *
     * <p>This is the one and only time the secret crosses the wire. The client
     * should show it, let the user scan or type it, and never store it.
     */
    @PostMapping("/enrol")
    public ResponseEntity<ApiResponse<MfaEnrolmentView>> enrol(
            @AuthenticationPrincipal HmsUserDetails user) {

        MfaService.Enrolment enrolment =
            mfaService.beginEnrolment(user.getId(), user.getTenantId(), user.getUsername());

        return ResponseEntity.ok(ApiResponse.ok(
            "Scan this in your authenticator app, then confirm with a code",
            new MfaEnrolmentView(enrolment.secret(), enrolment.provisioningUri())));
    }

    /**
     * Confirm enrolment with a generated code, and receive the recovery codes.
     *
     * <p>The recovery codes are returned here and never again. The response is
     * the only copy the user will ever see, which the client must say plainly
     * before the user navigates away.
     */
    @PostMapping("/enrol/confirm")
    public ResponseEntity<ApiResponse<MfaRecoveryCodesView>> confirm(
            @AuthenticationPrincipal HmsUserDetails user,
            @RequestBody MfaCodeRequest req) {

        List<String> codes = mfaService.confirmEnrolment(user.getId(), req.code());
        return ResponseEntity.ok(ApiResponse.ok(
            "Multi-factor authentication is on. Save these recovery codes now — "
            + "they will not be shown again",
            new MfaRecoveryCodesView(codes)));
    }

    /**
     * Clear another user's second factor. Break-glass only.
     *
     * <p>For a user who has lost both their device and their recovery codes.
     * It is a genuine weakening of that account and is audited as one.
     */
    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasPermission('MFA_ADMIN','')")
    public ResponseEntity<ApiResponse<Void>> reset(
            @PathVariable UUID userId,
            @AuthenticationPrincipal HmsUserDetails actor) {

        mfaService.resetFor(userId, actor.getId());
        return ResponseEntity.ok(ApiResponse.ok(
            "Multi-factor authentication cleared. This account is password-only "
            + "until the user enrols again"));
    }

    public record MfaStatusView(String mode, boolean enrolled, boolean confirmed,
                                long recoveryCodesRemaining, boolean privileged) {}

    public record MfaEnrolmentView(String secret, String provisioningUri) {}

    public record MfaRecoveryCodesView(List<String> recoveryCodes) {}

    public record MfaCodeRequest(String code) {}
}
