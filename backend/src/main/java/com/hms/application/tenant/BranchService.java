package com.hms.application.tenant;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Branch management within a tenant. Intended for HOSPITAL_ADMIN (and SUPERADMIN impersonating a
 * tenant). All operations are scoped to the active tenant from {@link TenantContext}, so a hospital
 * admin can only ever see or modify branches of their own hospital.
 */
@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchJpaRepository branchRepo;

    @Transactional(readOnly = true)
    public List<BranchEntity> listForCurrentTenant() {
        return branchRepo.findAllByTenantId(TenantContext.require());
    }

    @Transactional(readOnly = true)
    public BranchEntity get(UUID branchId) {
        UUID tenantId = TenantContext.require();
        return branchRepo.findByIdAndTenantId(branchId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
    }

    @Transactional
    public BranchEntity create(String code, String name) {
        UUID tenantId = TenantContext.require();
        String normalizedCode = code == null ? "" : code.trim().toUpperCase();
        if (normalizedCode.isBlank()) throw new BusinessRuleViolationException("Branch code is required");
        if (name == null || name.isBlank()) throw new BusinessRuleViolationException("Branch name is required");
        if (branchRepo.existsByTenantIdAndCode(tenantId, normalizedCode)) {
            throw new BusinessRuleViolationException(
                "Branch code '" + normalizedCode + "' already exists in this hospital");
        }
        BranchEntity b = new BranchEntity();
        b.setTenantId(tenantId);
        b.setCode(normalizedCode);
        b.setName(name.trim());
        b.setDefault(false);
        b.setStatus((short) 1);
        return branchRepo.save(b);
    }

    @Transactional
    public BranchEntity update(UUID branchId, String name, Short status) {
        BranchEntity b = get(branchId);
        if (name != null && !name.isBlank()) b.setName(name.trim());
        if (status != null) {
            if (b.isDefault() && status == 0) {
                throw new BusinessRuleViolationException("The default branch cannot be deactivated");
            }
            b.setStatus(status);
        }
        return branchRepo.save(b);
    }
}
