package com.hms.application.role;
import com.hms.api.role.request.CreateRoleRequest;
import com.hms.api.role.response.RoleResponse;
import com.hms.api.feature.response.FeatureResponse;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.CrossTenantAccessException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.shared.*;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.security.FeaturePermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class RoleManagementService {
    private final RoleJpaRepository roleRepo;
    private final FeatureJpaRepository featureRepo;
    private final FeaturePermissionCacheService permissionCacheService;

    @Transactional @CacheEvict(cacheNames = "featurePermissions", allEntries = true)
    public RoleResponse createRole(CreateRoleRequest req) {
        UUID tenantId = TenantContext.require();
        UUID branchId = BranchContext.require();
        if (roleRepo.findByNameAndTenantIdAndBranchId(req.name(), tenantId, branchId).isPresent()) {
            throw new BusinessRuleViolationException("Role '" + req.name() + "' already exists in this branch");
        }
        RoleEntity role = new RoleEntity();
        role.setName(req.name());
        role.setDescription(req.description());
        role.setStatus(req.status() != null ? req.status() : (short) 1);
        role.setTenantId(tenantId);
        role.setBranchId(branchId);
        
        List<FeatureEntity> selectedFeatures = featureRepo.findAllById(req.featureIds());
        for (FeatureEntity f : selectedFeatures) {
            if (!tenantId.equals(f.getTenantId())) {
                throw new CrossTenantAccessException("Feature " + f.getId() + " does not belong to tenant " + tenantId);
            }
        }
        role.setFeatures(new HashSet<>(selectedFeatures));
        
        RoleEntity saved = roleRepo.save(role);
        permissionCacheService.rebuildCacheForTenant(tenantId);
        return toResponse(saved);
    }

    @Transactional @CacheEvict(cacheNames = "featurePermissions", allEntries = true)
    public RoleResponse updateRole(UUID roleId, CreateRoleRequest req) {
        UUID tenantId = TenantContext.require();
        UUID branchId = BranchContext.require();
        // Explicit tenant check (defence-in-depth on top of the @PostLoad guard).
        RoleEntity role = roleRepo.findByIdAndTenantIdAndBranchId(roleId, tenantId, branchId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (role.getBranchId() == null) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.hms.security.HmsUserDetails principal) {
                if (!principal.isSuperAdmin() && !principal.isHospitalAdmin()) {
                    throw new BusinessRuleViolationException("You do not have permission to modify a tenant-wide role.");
                }
            }
        }

        role.setName(req.name());
        role.setDescription(req.description());
        role.setStatus(req.status() != null ? req.status() : (short) 1);
        
        List<FeatureEntity> selectedFeatures = featureRepo.findAllById(req.featureIds());
        for (FeatureEntity f : selectedFeatures) {
            if (!tenantId.equals(f.getTenantId())) {
                throw new CrossTenantAccessException("Feature " + f.getId() + " does not belong to tenant " + tenantId);
            }
        }
        role.setFeatures(new HashSet<>(selectedFeatures));
        
        RoleEntity saved = roleRepo.save(role);
        permissionCacheService.rebuildCacheForTenant(tenantId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getAll() {
        // @Filter already narrows to the current tenant; the explicit param is belt-and-braces.
        return roleRepo.findAllActiveWithFeaturesByTenantAndBranch(TenantContext.require(), BranchContext.get())
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<FeatureResponse> getAllFeatures() {
        return featureRepo.findAllByTenantId(TenantContext.require()).stream()
            .map(f -> new FeatureResponse(f.getId(), f.getFeatureKey(), f.getDescription(), f.getModule()))
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getFeaturesForCurrentUser(String module) {
        return permissionCacheService.getCurrentUserFeatureMap(module);
    }

    private RoleResponse toResponse(RoleEntity r) {
        Set<RoleResponse.FeatureSummary> features = r.getFeatures().stream()
            .map(f -> new RoleResponse.FeatureSummary(f.getId(), f.getFeatureKey(), f.getDescription(), f.getModule()))
            .collect(Collectors.toSet());
        return new RoleResponse(r.getId(), r.getName(), r.getDescription(), r.getStatus(), features);
    }
}
