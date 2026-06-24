package com.hms.api.auth;
import com.hms.api.shared.ApiResponse;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.security.HmsUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
import java.util.UUID;

@RestController @RequestMapping("/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final com.hms.infrastructure.settings.SettingsRegistryImpl settingsRegistry;
    private final TenantJpaRepository tenantRepo;
    private final BranchJpaRepository branchRepo;
    private final com.hms.security.FeaturePermissionCacheService permissionCacheService;
    private final com.hms.application.user.AuthForgotPasswordService forgotPasswordService;

    @PostMapping({"/login", "/session"})
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest req,
                                                            HttpServletRequest request) {
        // No tenant/branch is supplied at login. Usernames are globally unique, so the user is
        // resolved by username alone and their tenant + branch come from the stored account.
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval((settingsRegistry.getSessionTimeoutMinutes() * 60) + 180);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext());

        HmsUserDetails user = (HmsUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok("Login successful", toResponse(user)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse>> me() {
        HmsUserDetails user = (HmsUserDetails) SecurityContextHolder.getContext()
            .getAuthentication().getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok("OK", toResponse(user)));
    }

    @GetMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setMaxInactiveInterval((settingsRegistry.getSessionTimeoutMinutes() * 60) + 180);
        }
        return ResponseEntity.ok(ApiResponse.ok("Session refreshed"));
    }

    private LoginResponse toResponse(HmsUserDetails user) {
        UUID tenantId = user.getTenantId();
        String tenantName = null;
        if (tenantId != null) {
            tenantName = tenantRepo.findById(tenantId).map(TenantEntity::getName).orElse(null);
        }
        UUID branchId = user.getBranchId();
        String branchName = null;
        if (branchId != null) {
            branchName = branchRepo.findById(branchId).map(BranchEntity::getName).orElse(null);
        }
        
        Set<String> featureKeys = user.isSuperAdmin()
            ? user.getFeatureKeys()
            : permissionCacheService.getFeatureKeysForRoles(tenantId, user.getRoleNames());

        return new LoginResponse(user.getId(), user.getUsername(), featureKeys,
            user.isSuperAdmin(), user.isHospitalAdmin(), user.getConsultantId(), user.getDepartmentId(),
            tenantId, tenantName, branchId, branchName, user.getRoleNames());
    }

    @PostMapping("/forgot-password/request")
    public ResponseEntity<ApiResponse<Void>> requestOtp(@RequestBody ForgotPasswordRequest req) {
        forgotPasswordService.requestForgotPasswordOtp(req.email());
        return ResponseEntity.ok(ApiResponse.ok("OTP sent to your email successfully"));
    }

    @PostMapping("/forgot-password/verify")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@RequestBody VerifyOtpRequest req) {
        forgotPasswordService.verifyOtp(req.email(), req.otp());
        return ResponseEntity.ok(ApiResponse.ok("OTP verified successfully"));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody ResetPasswordRequest req) {
        forgotPasswordService.resetPassword(req.email(), req.otp(), req.newPassword(), req.confirmPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully"));
    }

    /** Note: no tenantSlug — login takes only username + password. */
    public record LoginRequest(String username, String password) {}

    public record LoginResponse(UUID id, String username, Set<String> featureKeys,
        boolean isSuperAdmin, boolean isHospitalAdmin, UUID consultantId, UUID departmentId,
        UUID tenantId, String tenantName, UUID branchId, String branchName, Set<String> roles) {}

    public record ForgotPasswordRequest(String email) {}
    public record VerifyOtpRequest(String email, String otp) {}
    public record ResetPasswordRequest(String email, String otp, String newPassword, String confirmPassword) {}
}
