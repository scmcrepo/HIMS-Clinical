package com.hms.security;

import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Source-of-truth, in-memory authorization cache: featureKey -> Set&lt;roleName&gt;.
 *
 * <p>Built from the {@code role_features} table at startup and rebuilt after every
 * role mutation (see {@link com.hms.application.role.RoleManagementService}). Because
 * authorization is evaluated against this live map keyed by the user's <b>roles</b>
 * (which are stable for a session), any permission change an admin makes in the
 * "Roles &amp; Permissions" screen takes effect <b>immediately</b> for all users of
 * that role — no re-login or server restart required.
 *
 * <p>This is the server-side complement to {@code GET /feature/getFeaturesByCurrentUser}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeaturePermissionCacheService {

    private final RoleJpaRepository roleRepo;

    /** featureKey -> set of role names permitted to use it. */
    private volatile Map<String, Set<String>> featureRolesCache = new ConcurrentHashMap<>();

    /** Build once the application context is ready and the DB is migrated. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        rebuildCache();
    }

    /** Rebuild the whole map from the role -> feature assignments in the database. */
    @Transactional(readOnly = true)
    public void rebuildCache() {
        Map<String, Set<String>> newMap = new ConcurrentHashMap<>();
        roleRepo.findAllActiveWithFeatures().forEach(role ->
            role.getFeatures().forEach(feature ->
                newMap.computeIfAbsent(feature.getFeatureKey(), k -> ConcurrentHashMap.newKeySet())
                      .add(role.getName())));
        this.featureRolesCache = newMap;
        log.info("RBAC permission cache rebuilt: {} feature key(s) mapped to roles", newMap.size());
    }

    /**
     * Core authorization decision.
     *
     * @param userRoleNames the authenticated user's role names (bare, e.g. "DOCTOR")
     * @param featureKey    the feature being accessed
     * @return true if any of the user's roles is permitted for this feature
     */
    public boolean isAllowed(Set<String> userRoleNames, String featureKey) {
        if (featureKey == null || featureKey.isBlank()) return false; // explicit key required
        if (userRoleNames == null || userRoleNames.isEmpty()) return false;
        Set<String> permittedRoles = featureRolesCache.get(featureKey);
        if (permittedRoles == null || permittedRoles.isEmpty()) return false; // unknown / unassigned
        for (String role : userRoleNames) {
            if (permittedRoles.contains(role)) return true;
        }
        return false;
    }

    /**
     * Returns {@code Map<featureKey, true>} for every feature the current user can access,
     * optionally filtered to a single module. Used by the AngularJS-style frontend feature gate.
     */
    public Map<String, Boolean> getCurrentUserFeatureMap(String module) {
        Set<String> roleNames = currentUserRoleNames();
        if (roleNames.isEmpty()) return Collections.emptyMap();
        boolean superAdmin = roleNames.contains("SUPERADMIN");
        Map<String, Boolean> result = new HashMap<>();
        featureRolesCache.forEach((featureKey, roles) -> {
            boolean hasAccess = superAdmin || roles.stream().anyMatch(roleNames::contains);
            if (hasAccess) result.put(featureKey, true);
        });
        return result; // module currently informational; keys are globally unique
    }

    /** Extract the current user's bare role names from the security context. */
    private Set<String> currentUserRoleNames() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Collections.emptySet();
        if (auth.getPrincipal() instanceof HmsUserDetails details) {
            return details.getRoleNames();
        }
        // Fallback: derive from ROLE_-prefixed authorities
        Set<String> names = new HashSet<>();
        auth.getAuthorities().forEach(a -> {
            String s = a.getAuthority();
            if (s.startsWith("ROLE_")) names.add(s.substring(5));
        });
        return names;
    }
}
