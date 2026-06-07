package com.hms.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Feature-key authorization, driven by the live {@link FeaturePermissionCacheService}.
 *
 * <p>Usage in controllers: {@code @PreAuthorize("hasPermission('FEATURE_KEY','')")}
 * (the key may be supplied as either argument; both positions are resolved).
 *
 * <p>Decision order:
 * <ol>
 *   <li>No authentication &rarr; deny.</li>
 *   <li>SUPERADMIN &rarr; allow (full bypass).</li>
 *   <li>Otherwise: look up the feature's permitted roles in the live cache and allow
 *       iff one of the user's roles is in that set. This means the result reflects
 *       whatever the admin has configured in the Roles &amp; Permissions screen,
 *       with no re-login required.</li>
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

        Set<String> roleNames = resolveRoleNames(authentication);

        // Super Admin bypass
        if (roleNames.contains("SUPERADMIN")) return true;

        String featureKey = resolveKey(permission, targetDomainObject);
        if (featureKey == null) return false;

        boolean allowed = cache.isAllowed(roleNames, featureKey);
        if (!allowed) {
            log.warn("DENY user[{}] roles{} feature[{}]", authentication.getName(), roleNames, featureKey);
        }
        return allowed;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(authentication, targetType, permission);
    }

    /** The feature key may arrive in either argument; prefer a non-blank one. */
    private String resolveKey(Object permission, Object target) {
        if (permission != null && !permission.toString().isBlank()) return permission.toString();
        if (target != null && !target.toString().isBlank()) return target.toString();
        return null;
    }

    /** Bare role names (no ROLE_ prefix) for the authenticated user. */
    private Set<String> resolveRoleNames(Authentication auth) {
        if (auth.getPrincipal() instanceof HmsUserDetails details) {
            return details.getRoleNames();
        }
        Set<String> names = new HashSet<>();
        for (GrantedAuthority a : auth.getAuthorities()) {
            String s = a.getAuthority();
            if (s.startsWith("ROLE_")) names.add(s.substring(5));
        }
        return names;
    }
}
