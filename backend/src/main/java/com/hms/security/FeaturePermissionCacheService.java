package com.hms.security;

import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import com.hms.infrastructure.tenant.TenantContext;
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
 * In-memory authorization cache, now keyed by tenant:
 * {@code tenantId -> (featureKey -> Set<roleId>)}.
 *
 * <p>Each tenant has its own role/feature graph (seeded per tenant). Because roles can share
 * names across branches, the cache maps feature keys to exact role UUIDs instead of strings.
 * Role mutations rebuild only the affected tenant's slice
 * ({@link #rebuildCacheForTenant(UUID)}) — O(roles-in-one-tenant), not O(all-tenants).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeaturePermissionCacheService {

    private final RoleJpaRepository roleRepo;

    /** tenantId -> (featureKey -> set of role IDs permitted to use it). */
    private final Map<UUID, Map<String, Set<UUID>>> tenantFeatureRolesCache = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        rebuildAll();
    }

    /** Full rebuild across all tenants (startup / admin-triggered reload). */
    @Transactional(readOnly = true)
    public void rebuildAll() {
        tenantFeatureRolesCache.clear();
        for (RoleEntity role : roleRepo.findAllActiveWithFeatures()) {
            UUID tenantId = role.getTenantId();
            if (tenantId == null) continue; // defensive: roles must be tenant-scoped
            Map<String, Set<UUID>> tenantMap =
                tenantFeatureRolesCache.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
            role.getFeatures().forEach(feature ->
                tenantMap.computeIfAbsent(feature.getFeatureKey(), k -> ConcurrentHashMap.newKeySet())
                         .add(role.getId()));
        }
        log.info("RBAC permission cache rebuilt for {} tenant(s)", tenantFeatureRolesCache.size());
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /** Rebuild just one tenant's slice after a role/feature change in that tenant. */
    @Transactional(readOnly = true)
    public void rebuildCacheForTenant(UUID tenantId) {
        if (tenantId == null) return;
        
        UUID originalBranchId = com.hms.infrastructure.tenant.BranchContext.get();
        try {
            // Temporarily clear branch context so that TenantFilterAspect disables the branchFilter
            // when it intercepts the upcoming roleRepo call. This ensures we fetch ALL branches' roles.
            com.hms.infrastructure.tenant.BranchContext.clear();
            
            Map<String, Set<UUID>> tenantMap = new ConcurrentHashMap<>();
            roleRepo.findAllActiveWithFeaturesByTenant(tenantId).forEach(role ->
                role.getFeatures().forEach(feature ->
                    tenantMap.computeIfAbsent(feature.getFeatureKey(), k -> ConcurrentHashMap.newKeySet())
                             .add(role.getId())));
            tenantFeatureRolesCache.put(tenantId, tenantMap);
            log.info("RBAC permission cache rebuilt for tenant {}: {} feature key(s)",
                     tenantId, tenantMap.size());
        } finally {
            // Restore original branch context
            if (originalBranchId != null) {
                com.hms.infrastructure.tenant.BranchContext.set(originalBranchId);
            }
        }
    }

    /**
     * Core authorization decision, scoped to a tenant.
     * SUPERADMIN (tenantId == null) is handled upstream in HmsPermissionEvaluator (full bypass),
     * so a null tenant here is always a deny.
     */
    public boolean isAllowed(Set<UUID> userRoleIds, String featureKey, UUID tenantId) {
        if (tenantId == null) return false;
        if (featureKey == null || featureKey.isBlank()) return false;
        if (userRoleIds == null || userRoleIds.isEmpty()) return false;
        Map<String, Set<UUID>> tenantMap = tenantFeatureRolesCache.get(tenantId);
        if (tenantMap == null) return false;
        Set<UUID> permittedRoles = tenantMap.get(featureKey);
        if (permittedRoles == null || permittedRoles.isEmpty()) return false;
        for (UUID roleId : userRoleIds) {
            if (permittedRoles.contains(roleId)) return true;
        }
        return false;
    }

    public Set<String> getFeatureKeysForRoles(UUID tenantId, Set<UUID> roleIds) {
        if (tenantId == null || roleIds == null || roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        Map<String, Set<UUID>> tenantMap = tenantFeatureRolesCache.get(tenantId);
        if (tenantMap == null) return Collections.emptySet();
        Set<String> activeKeys = new HashSet<>();
        tenantMap.forEach((featureKey, roles) -> {
            if (roles.stream().anyMatch(roleIds::contains)) {
                activeKeys.add(featureKey);
            }
        });
        return activeKeys;
    }

    /**
     * {@code Map<featureKey, true>} for every feature the current user can access, within
     * the current tenant. Used by the frontend feature gate.
     */
    public Map<String, Boolean> getCurrentUserFeatureMap(String module) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof HmsUserDetails details)) {
            return Collections.emptyMap();
        }
        if (details.isSuperAdmin()) {
            // Platform superadmin: surface every feature across the impersonated tenant (if any).
            UUID impersonated = TenantContext.get();
            Map<String, Set<UUID>> tenantMap = impersonated == null
                ? Collections.emptyMap()
                : tenantFeatureRolesCache.getOrDefault(impersonated, Collections.emptyMap());
            Map<String, Boolean> all = new HashMap<>();
            tenantMap.keySet().forEach(k -> all.put(k, true));
            return all;
        }

        UUID tenantId = details.getTenantId();
        UUID currentBranchId = com.hms.infrastructure.tenant.BranchContext.get();
        Set<UUID> roleIds = details.getActiveRoleIds(currentBranchId);
        Map<String, Set<UUID>> tenantMap = tenantId == null
            ? Collections.emptyMap()
            : tenantFeatureRolesCache.getOrDefault(tenantId, Collections.emptyMap());

        Map<String, Boolean> result = new HashMap<>();
        tenantMap.forEach((featureKey, roles) -> {
            if (roles.stream().anyMatch(roleIds::contains)) result.put(featureKey, true);
        });
        return result;
    }
}
