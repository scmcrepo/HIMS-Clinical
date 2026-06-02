package com.hms.security;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.io.Serializable;

/**
 * Evaluates feature-key-based permissions from the authenticated user's authorities.
 * Usage in controllers: @PreAuthorize("hasPermission(null, 'BILL_CREATE')")
 */
@Component
@Slf4j
public class HmsPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null) return false;
        
        // Super Admin bypass
        if (authentication.getPrincipal() instanceof HmsUserDetails userDetails) {
            if (userDetails.isSuperAdmin()) return true;
        }

        // Feature key can be in 'permission' or 'targetDomainObject'
        String resolvedKey = null;
        if (permission != null && !permission.toString().isBlank()) {
            resolvedKey = permission.toString();
        } else if (targetDomainObject != null && !targetDomainObject.toString().isBlank()) {
            resolvedKey = targetDomainObject.toString();
        }

        if (resolvedKey == null) return false;

        final String featureKey = resolvedKey;
        log.info("Checking featureKey: {} for user: {}", featureKey, authentication.getName());
        
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(auth -> auth.equals(featureKey));
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return hasPermission(authentication, targetType, permission);
    }
}
