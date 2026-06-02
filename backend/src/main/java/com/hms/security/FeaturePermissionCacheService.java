package com.hms.security;
import com.hms.infrastructure.persistence.shared.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Builds and caches the featureKey → Set<roleName> permission map.
 * Rebuilt after every Role mutation by RoleManagementService.
 * The map is the server-side complement to /feature/getFeaturesByCurrentUser.
 */
@Service @RequiredArgsConstructor @Slf4j
public class FeaturePermissionCacheService {
    private final UserJpaRepository userRepo;
    private volatile Map<String, Set<String>> featureRolesCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    @CacheEvict(cacheNames = "featurePermissions", allEntries = true)
    public void rebuildCache() {
        Map<String, Set<String>> newMap = new ConcurrentHashMap<>();
        userRepo.findAll().forEach(user ->
            user.getRoles().forEach(role ->
                role.getFeatures().forEach(feature -> {
                    newMap.computeIfAbsent(feature.getFeatureKey(), k -> new HashSet<>()).add(role.getName());
                })));
        this.featureRolesCache = newMap;
        log.info("Permission cache rebuilt: {} feature keys", newMap.size());
    }

    public boolean hasPermission(String username, String featureKey) {
        if (featureKey == null || featureKey.isBlank()) return true;
        Set<String> permittedRoles = featureRolesCache.get(featureKey);
        if (permittedRoles == null || permittedRoles.isEmpty()) return false;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
            .anyMatch(a -> permittedRoles.contains(a.getAuthority()));
    }

    public Map<String, Boolean> getCurrentUserFeatureMap(String module) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Collections.emptyMap();
        Map<String, Boolean> result = new HashMap<>();
        featureRolesCache.forEach((featureKey, roles) -> {
            if (module == null || featureKey.startsWith(module.toUpperCase())) {
                boolean hasAccess = auth.getAuthorities().stream()
                    .anyMatch(a -> roles.contains(a.getAuthority()));
                if (hasAccess) result.put(featureKey, true);
            }
        });
        return result;
    }
}
