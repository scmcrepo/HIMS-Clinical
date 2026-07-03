package com.hms.api.auth;
import com.hms.api.shared.ApiResponse;
import com.hms.application.user.LoginAttemptService;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.security.HmsUserDetails;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.shared.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final com.hms.infrastructure.settings.SettingsRegistryImpl settingsRegistry;
    private final TenantJpaRepository tenantRepo;
    private final BranchJpaRepository branchRepo;
    private final UserJpaRepository userRepo;
    private final com.hms.security.FeaturePermissionCacheService permissionCacheService;
    private final com.hms.application.user.AuthForgotPasswordService forgotPasswordService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final com.hms.security.HmsUserDetailsService userDetailsService;

    public record BranchSummary(UUID id, String name) {}
    public record MultiBranchResponse(String status, List<BranchSummary> branches) {}

    @PostMapping({"/login", "/session"})
    @Transactional(noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ApiResponse<Object>> login(@RequestBody LoginRequest req,
                                                            HttpServletRequest request) {
        // Step 1: Resolve the user by username (including locked accounts)
        UserEntity userEntity = userRepo.findByUsernameWithRolesAndFeaturesIncludingLocked(req.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        // Step 2: If the account is locked/inactive, reject immediately
        if (userEntity.getStatus() != 1 || userEntity.isAccountLocked()) {
            throw new DisabledException("Your account has been locked due to too many failed login attempts. Please contact your administrator");
        }

        // Step 3: Check tenant/branch active status
        if (userEntity.getTenantId() != null && userEntity.getTenant() != null && !userEntity.getTenant().isActive()) {
            throw new DisabledException("Hospital/Tenant is inactive");
        }
        if (userEntity.getBranchId() != null && userEntity.getBranch() != null && !userEntity.getBranch().isActive()) {
            throw new DisabledException("Branch is inactive");
        }

        // Step 4: Verify password manually
        if (!passwordEncoder.matches(req.password(), userEntity.getPasswordHash())) {
            int remaining = loginAttemptService.handleFailedAttempt(userEntity);
            if (remaining <= 0) {
                throw new DisabledException(
                    "Your account has been locked due to too many failed login attempts. Please contact your administrator");
            }
            throw new BadCredentialsException(
                "Invalid credentials. " + remaining + " attempt(s) remaining before account lockout");
        }

        // Step 5: Password correct — reset failed attempts counter
        loginAttemptService.handleSuccessfulLogin(userEntity);

        // Step 6: Build the authentication principal via UserDetailsService
        //         (this reloads roles, features, consultant, departments etc.)
        HmsUserDetails userDetails = (HmsUserDetails) userDetailsService.loadUserByUsername(req.username());

        List<BranchEntity> activeBranches = userEntity.getBranches().stream()
            .filter(BranchEntity::isActive)
            .collect(java.util.stream.Collectors.toList());
        if (activeBranches.isEmpty() && userEntity.getBranchId() != null) {
            branchRepo.findById(userEntity.getBranchId())
                .filter(BranchEntity::isActive)
                .ifPresent(activeBranches::add);
        }
        final UUID finalSelectedBranchId = req.branchId();
        UUID selectedBranchId = finalSelectedBranchId;

        // If the user has multiple assigned branches and is a regular user, they must select a branch
        if (!userDetails.isHospitalAdmin() && !userDetails.isSuperAdmin() && activeBranches.size() > 1) {
            if (finalSelectedBranchId == null) {
                List<BranchSummary> branches = activeBranches.stream()
                    .map(b -> new BranchSummary(b.getId(), b.getName()))
                    .toList();
                return ResponseEntity.ok(ApiResponse.ok("Multiple branches found", new MultiBranchResponse("MULTIPLE_BRANCHES", branches)));
            }

            boolean hasAccess = activeBranches.stream().anyMatch(b -> b.getId().equals(finalSelectedBranchId));
            if (!hasAccess) {
                throw new BadCredentialsException("Access to requested branch is denied or branch is inactive");
            }
        } else if (finalSelectedBranchId == null) {
            // Default branch handling with fallback logic
            UUID defaultBranchId = userEntity.getBranchId();
            if (defaultBranchId != null && activeBranches.stream().anyMatch(b -> b.getId().equals(defaultBranchId))) {
                selectedBranchId = defaultBranchId;
            } else if (!activeBranches.isEmpty()) {
                selectedBranchId = activeBranches.get(0).getId();
            }
        }

        Set<UUID> authorizedBranchIds = activeBranches.stream()
            .map(BranchEntity::getId)
            .collect(java.util.stream.Collectors.toSet());

        HmsUserDetails activeUserDetails = new HmsUserDetails(
            userDetails.getId(), userDetails.getUsername(), userDetails.getPassword(),
            userDetails.isAccountLocked(), userDetails.getFeatureKeys(), userDetails.getRoleNames(), userDetails.getRoleIds(),
            userDetails.getBranchRoleIds(),
            userDetails.getConsultantId(), userDetails.getDepartmentId(), userDetails.getTenantId(),
            selectedBranchId, userDetails.getDepartmentIds(), authorizedBranchIds
        );

        Authentication finalAuth = new UsernamePasswordAuthenticationToken(
            activeUserDetails, null, activeUserDetails.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(finalAuth);
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval((settingsRegistry.getSessionTimeoutMinutes() * 60) + 180);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext());

        return ResponseEntity.ok(ApiResponse.ok("Login successful", toResponse(activeUserDetails)));
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
            : permissionCacheService.getFeatureKeysForRoles(tenantId, user.getActiveRoleIds(branchId));

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

    /** Note: no tenantSlug — login takes only username + password + optional branchId. */
    public record LoginRequest(String username, String password, UUID branchId) {}

    public record LoginResponse(UUID id, String username, Set<String> featureKeys,
        boolean isSuperAdmin, boolean isHospitalAdmin, UUID consultantId, UUID departmentId,
        UUID tenantId, String tenantName, UUID branchId, String branchName, Set<String> roles) {}

    public record ForgotPasswordRequest(String email) {}
    public record VerifyOtpRequest(String email, String otp) {}
    public record ResetPasswordRequest(String email, String otp, String newPassword, String confirmPassword) {}
}
