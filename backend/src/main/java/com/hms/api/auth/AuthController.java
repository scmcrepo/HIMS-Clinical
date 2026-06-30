package com.hms.api.auth;
import com.hms.api.shared.ApiResponse;
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
    private final com.hms.security.HmsUserDetailsService userDetailsService;

    public record BranchSummary(UUID id, String name) {}
    public record MultiBranchResponse(String status, List<BranchSummary> branches) {}

    @PostMapping({"/login", "/session"})
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Object>> login(@RequestBody LoginRequest req,
                                                            HttpServletRequest request) {
        // No tenant/branch is supplied at login. Usernames are globally unique, so the user is
        // resolved by username alone and their tenant + branch come from the stored account.
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.username(), req.password()));

        HmsUserDetails userDetails = (HmsUserDetails) auth.getPrincipal();
        UserEntity userEntity = userRepo.findById(userDetails.getId())
            .orElseThrow(() -> new BadCredentialsException("User not found"));

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
            activeUserDetails, auth.getCredentials(), activeUserDetails.getAuthorities()
        );

        SecurityContextHolder.getContext().setAuthentication(finalAuth);
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval((settingsRegistry.getSessionTimeoutMinutes() * 60) + 180);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            SecurityContextHolder.getContext());

        return ResponseEntity.ok(ApiResponse.ok("Login successful", toResponse(activeUserDetails)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse>> me(HttpServletRequest request) {
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth == null || !(existingAuth.getPrincipal() instanceof HmsUserDetails user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        HmsUserDetails freshUser = (HmsUserDetails) userDetailsService.loadUserByUsername(user.getUsername());
        
        // Preserve selected branch and authorized branches
        HmsUserDetails updatedUser = new HmsUserDetails(
            freshUser.getId(), freshUser.getUsername(), freshUser.getPassword(),
            freshUser.isAccountLocked(), freshUser.getFeatureKeys(), freshUser.getRoleNames(), freshUser.getRoleIds(),
            freshUser.getBranchRoleIds(),
            freshUser.getConsultantId(), freshUser.getDepartmentId(), freshUser.getTenantId(),
            user.getBranchId() != null ? user.getBranchId() : freshUser.getBranchId(),
            freshUser.getDepartmentIds(), freshUser.getAuthorizedBranchIds()
        );

        Authentication newAuth = new UsernamePasswordAuthenticationToken(
            updatedUser, existingAuth.getCredentials(), updatedUser.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(newAuth);
        
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
        }

        return ResponseEntity.ok(ApiResponse.ok("OK", toResponse(updatedUser)));
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
