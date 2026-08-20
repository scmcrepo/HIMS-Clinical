package com.hms.api.portal;

import com.hms.api.portal.request.PortalRequests;
import com.hms.api.portal.response.PortalResponses;
import com.hms.api.shared.ApiResponse;
import com.hms.application.portal.PortalAuthService;
import com.hms.application.portal.PortalErrorCode;
import com.hms.application.portal.PortalException;
import com.hms.application.portal.PortalLookupService;
import com.hms.application.portal.PortalOtpService;
import com.hms.application.portal.PortalProperties;
import com.hms.security.portal.PortalPrincipalFactory;
import com.hms.security.portal.PortalTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Patient portal authentication (WO-017).
 *
 * <p>Four steps, and the shape is deliberate:
 *
 * <pre>
 *   POST /portal/auth/otp/request   public    → code by SMS
 *   POST /portal/auth/otp/verify    public    → identity token + candidate list
 *   POST /portal/auth/session       identity  → access + refresh tokens
 *   POST /portal/auth/refresh       token     → rotated pair
 * </pre>
 *
 * <p>The identity token proves possession of a number. It reads no clinical data
 * and holds no patient id. Choosing a profile is a server-side authorisation
 * decision re-checked against the verified number, not a client-side choice the
 * server takes on trust.
 */
@RestController
@RequestMapping("/portal/auth")
@RequiredArgsConstructor
@Slf4j
public class PortalAuthController {

    private final PortalOtpService otpService;
    private final PortalLookupService lookupService;
    private final PortalAuthService authService;
    private final PortalTokenService tokenService;
    private final PortalProperties properties;

    @PostMapping("/otp/request")
    public ResponseEntity<ApiResponse<PortalResponses.OtpRequested>> requestOtp(
            @Valid @RequestBody PortalRequests.OtpRequest body,
            HttpServletRequest request) {

        PortalOtpService.OtpChallengeIssued issued =
            otpService.issue(body.mobile(), sourceHash(request));

        return ResponseEntity.ok(ApiResponse.ok(
            "If this number is registered, a code has been sent",
            new PortalResponses.OtpRequested(
                issued.challengeId(),
                issued.expiresInSeconds(),
                issued.resendAvailableInSeconds())));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<PortalResponses.OtpVerified>> verifyOtp(
            @Valid @RequestBody PortalRequests.OtpVerify body) {

        String contactToken = properties.isOtpRequired()
            ? otpService.verify(body.challengeId(), body.mobile(), body.code())
            : otpService.bypassVerification(body.mobile());

        List<PortalLookupService.HospitalCandidate> candidates =
            lookupService.findCandidates(contactToken);

        String identityToken = tokenService.issueIdentityToken(contactToken);

        return ResponseEntity.ok(ApiResponse.ok(
            "Mobile number verified",
            new PortalResponses.OtpVerified(
                identityToken,
                Instant.now().plus(properties.getIdentityTokenTtl()),
                candidates.stream().map(PortalAuthController::toResponse).toList())));
    }

    @PostMapping("/session")
    @PreAuthorize("hasAuthority('PORTAL_IDENTITY')")
    public ResponseEntity<ApiResponse<PortalResponses.SessionTokens>> establishSession(
            @Valid @RequestBody PortalRequests.SessionExchange body) {

        PortalAuthService.IssuedSession issued = authService.establishSession(
            verifiedContactToken(),
            body.patientId(), body.tenantId(), body.branchId(), body.deviceLabel());

        return ResponseEntity.ok(ApiResponse.ok("Signed in", toTokens(issued)));
    }

    /**
     * Rotates the token pair.
     *
     * <p>Unauthenticated by chain configuration, because the access token has
     * expired by definition at the moment this is called. The refresh token in
     * the body is the credential.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<PortalResponses.SessionTokens>> refresh(
            @Valid @RequestBody PortalRequests.RefreshRequest body) {

        return ResponseEntity.ok(ApiResponse.ok(
            "Session refreshed", toTokens(authService.refresh(body.refreshToken()))));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('PORTAL_PATIENT')")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout((UUID) request.getAttribute("portalChainId"));
        return ResponseEntity.ok(ApiResponse.ok("Signed out"));
    }

    // ── internals ───────────────────────────────────────────────────────────

    /**
     * The OTP-verified number behind the current identity token.
     *
     * <p>Read from the principal, never from the request body. If it came from
     * the body, "which number was verified" would be a client-supplied claim and
     * the entire OTP step would be decorative.
     */
    static String verifiedContactToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !(auth.getPrincipal()
                    instanceof PortalPrincipalFactory.PortalIdentityPrincipal principal)) {
            throw new PortalException(
                PortalErrorCode.IDENTITY_TOKEN_REQUIRED, "portal.identity_token_required");
        }
        return principal.contactNumberToken();
    }

    /**
     * Salted hash of the caller address for rate limiting.
     *
     * <p>{@code X-Forwarded-For} is honoured because the app sits behind a proxy
     * and every request would otherwise share the proxy's address, collapsing
     * per-source limiting into a single global bucket. The header is
     * client-controllable, which is why it feeds a rate limit and nothing else.
     */
    private static String sourceHash(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String address = (forwarded != null && !forwarded.isBlank())
            ? forwarded.split(",")[0].trim()
            : request.getRemoteAddr();
        return PortalOtpService.hashSource(address, "portal-otp-source");
    }

    private static PortalResponses.SessionTokens toTokens(PortalAuthService.IssuedSession s) {
        return new PortalResponses.SessionTokens(
            s.accessToken(), s.refreshToken(),
            s.accessTokenExpiresAt(), s.refreshTokenExpiresAt());
    }

    private static PortalResponses.HospitalCandidate toResponse(
            PortalLookupService.HospitalCandidate h) {
        return new PortalResponses.HospitalCandidate(
            h.tenantId(), h.tenantName(), h.address(), h.contactNumber(), h.logoUrl(),
            h.patients().stream()
                .map(p -> new PortalResponses.PatientCandidate(
                    p.patientId(), p.fullName(), p.age(), p.gender(),
                    p.numberSequenceSuffix(), p.photoUrl(), p.branchId()))
                .toList(),
            h.branches().stream()
                .map(b -> new PortalResponses.BranchSummary(
                    b.branchId(), b.name(), b.code(), b.address(), b.contactNumber(),
                    b.isDefault(), b.isActive()))
                .toList());
    }
}
