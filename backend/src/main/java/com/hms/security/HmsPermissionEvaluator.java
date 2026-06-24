package com.hms.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

/**
 * Feature-key authorization, driven by the tenant-scoped {@link FeaturePermissionCacheService}.
 *
 * <p>Decision order:
 * <ol>
 *   <li>No authentication =&gt; deny.</li>
 *   <li>SUPERADMIN (platform user) =&gt; allow (full bypass).</li>
 *   <li>Otherwise look up the feature's permitted roles for the user's tenant and allow iff
 *       one of the user's roles is in that set.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HmsPermissionEvaluator implements PermissionEvaluator {

    private final FeaturePermissionCacheService cache;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        if (!(authentication.getPrincipal() instanceof HmsUserDetails user)) return false;

        // Platform super admin bypass.
        if (user.isSuperAdmin()) return true;

        String featureKey = resolveKey(permission, targetDomainObject);
        if (featureKey == null) return false;

        Set<String> roleNames = user.getRoleNames();
        UUID currentBranchId = com.hms.infrastructure.tenant.BranchContext.get();
        Set<UUID> roleIds = user.getActiveRoleIds(currentBranchId);
        UUID tenantId = user.getTenantId();

        boolean allowed = cache.isAllowed(roleIds, featureKey, tenantId);
        if (!allowed) {
            allowed = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals(featureKey));
        }
        try {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("/home/ssb/Desktop/HIMS-Clinical/backend/evaluator_debug.log"),
                String.format("Evaluating: user=%s, roles=%s, featureKey=%s, tenantId=%s, allowed=%s\n",
                              user.getUsername(), roleNames, featureKey, tenantId, allowed),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception e) {
            // ignore
        }
        if (!allowed) {
            log.warn("DENY user[{}] tenant[{}] roles{} feature[{}]",
                     user.getUsername(), tenantId, roleNames, featureKey);
        }
        return allowed;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(authentication, targetType, permission);
    }

    private String resolveKey(Object permission, Object target) {
        if (permission != null && !permission.toString().isBlank()) return permission.toString();
        if (target != null && !target.toString().isBlank()) return target.toString();
        return null;
    }
}
