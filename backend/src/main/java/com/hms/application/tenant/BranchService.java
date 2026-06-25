package com.hms.application.tenant;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import com.hms.infrastructure.tenant.TenantContext;
import java.util.HashSet;
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
    private final RoleJpaRepository roleRepo;
    private final jakarta.persistence.EntityManager entityManager;

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
    public BranchEntity create(String code, String name, String address, String contactNumber) {
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
        b.setAddress(address);
        b.setContactNumber(contactNumber);
        b.setDefault(false);
        b.setStatus((short) 1);
        BranchEntity saved = branchRepo.save(b);
        cloneTemplatesToBranch(tenantId, saved.getId());
        cloneRolesToBranch(tenantId, saved.getId());
        return saved;
    }

    @Transactional
    public BranchEntity update(UUID branchId, String name, String address, String contactNumber, Short status) {
        BranchEntity b = get(branchId);
        if (name != null && !name.isBlank()) b.setName(name.trim());
        if (address != null) b.setAddress(address);
        if (contactNumber != null) b.setContactNumber(contactNumber);
        if (status != null) {
            if (b.isDefault() && status == 0) {
                throw new BusinessRuleViolationException("The default branch cannot be deactivated");
            }
            b.setStatus(status);
        }
        return branchRepo.save(b);
    }

    private void cloneTemplatesToBranch(UUID tenantId, UUID branchId) {
        // Query templates from default hospital
        List<Object[]> caseSheetTemplates = entityManager.createNativeQuery(
            "SELECT id, name, specialization, visit_type, description, is_default, status " +
            "FROM case_sheet_templates " +
            "WHERE tenant_id = '00000000-0000-0000-0000-000000000001'")
            .getResultList();

        for (Object[] row : caseSheetTemplates) {
            UUID oldId = UUID.fromString(row[0].toString());
            String name = (String) row[1];
            String specialization = (String) row[2];
            String visitType = (String) row[3];
            String description = (String) row[4];
            boolean isDefault = (boolean) row[5];
            int statusVal = ((Number) row[6]).intValue();

            // Check if template already exists to be idempotent
            boolean exists = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM case_sheet_templates " +
                "WHERE tenant_id = :tenantId AND branch_id = :branchId AND name = :name AND specialization = :specialization AND visit_type = :visitType")
                .setParameter("tenantId", tenantId)
                .setParameter("branchId", branchId)
                .setParameter("name", name)
                .setParameter("specialization", specialization)
                .setParameter("visitType", visitType)
                .getSingleResult()).intValue() > 0;

            if (!exists) {
                UUID newId = UUID.randomUUID();
                entityManager.createNativeQuery(
                    "INSERT INTO case_sheet_templates (id, name, specialization, visit_type, description, is_default, status, created_at, modified_at, tenant_id, branch_id) " +
                    "VALUES (:newId, :name, :specialization, :visitType, :description, :isDefault, :status, NOW(), NOW(), :tenantId, :branchId)")
                    .setParameter("newId", newId)
                    .setParameter("name", name)
                    .setParameter("specialization", specialization)
                    .setParameter("visitType", visitType)
                    .setParameter("description", description)
                    .setParameter("isDefault", isDefault)
                    .setParameter("status", statusVal)
                    .setParameter("tenantId", tenantId)
                    .setParameter("branchId", branchId)
                    .executeUpdate();

                entityManager.createNativeQuery(
                    "INSERT INTO case_sheet_template_fields (id, template_id, field_key, label, field_type, section, display_order, is_required, placeholder, help_text, options, validation, default_value, is_visible, status, created_at, modified_at) " +
                    "SELECT gen_random_uuid(), :newId, field_key, label, field_type, section, display_order, is_required, placeholder, help_text, options, validation, default_value, is_visible, status, NOW(), NOW() " +
                    "FROM case_sheet_template_fields WHERE template_id = :oldId")
                    .setParameter("newId", newId)
                    .setParameter("oldId", oldId)
                    .executeUpdate();
            }
        }

        List<Object[]> dischargeTemplates = entityManager.createNativeQuery(
            "SELECT id, name, specialization, description, is_default, status " +
            "FROM discharge_summary_templates " +
            "WHERE tenant_id = '00000000-0000-0000-0000-000000000001'")
            .getResultList();

        for (Object[] row : dischargeTemplates) {
            UUID oldId = UUID.fromString(row[0].toString());
            String name = (String) row[1];
            String specialization = (String) row[2];
            String description = (String) row[3];
            boolean isDefault = (boolean) row[4];
            int statusVal = ((Number) row[5]).intValue();

            boolean exists = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM discharge_summary_templates " +
                "WHERE tenant_id = :tenantId AND branch_id = :branchId AND name = :name AND specialization = :specialization")
                .setParameter("tenantId", tenantId)
                .setParameter("branchId", branchId)
                .setParameter("name", name)
                .setParameter("specialization", specialization)
                .getSingleResult()).intValue() > 0;

            if (!exists) {
                UUID newId = UUID.randomUUID();
                entityManager.createNativeQuery(
                    "INSERT INTO discharge_summary_templates (id, name, specialization, description, is_default, status, created_at, modified_at, tenant_id, branch_id) " +
                    "VALUES (:newId, :name, :specialization, :description, :isDefault, :status, NOW(), NOW(), :tenantId, :branchId)")
                    .setParameter("newId", newId)
                    .setParameter("name", name)
                    .setParameter("specialization", specialization)
                    .setParameter("description", description)
                    .setParameter("isDefault", isDefault)
                    .setParameter("status", statusVal)
                    .setParameter("tenantId", tenantId)
                    .setParameter("branchId", branchId)
                    .executeUpdate();

                entityManager.createNativeQuery(
                    "INSERT INTO discharge_summary_template_fields (id, template_id, field_key, label, field_type, section, display_order, is_required, placeholder, help_text, options, validation, default_value, is_visible, status, created_at, modified_at) " +
                    "SELECT gen_random_uuid(), :newId, field_key, label, field_type, section, display_order, is_required, placeholder, help_text, options, validation, default_value, is_visible, status, NOW(), NOW() " +
                    "FROM discharge_summary_template_fields WHERE template_id = :oldId")
                    .setParameter("newId", newId)
                    .setParameter("oldId", oldId)
                    .executeUpdate();
            }
        }
    }

    private void cloneRolesToBranch(UUID tenantId, UUID branchId) {
        UUID defaultBranchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
            .map(BranchEntity::getId)
            .orElse(null);
        if (defaultBranchId == null) return;

        List<RoleEntity> defaultRoles = roleRepo.findAllActiveWithFeaturesByTenantAndBranch(tenantId, defaultBranchId);
        for (RoleEntity oldRole : defaultRoles) {
            // Only clone branch-scoped roles. Skip global/tenant-wide ones (branchId is null)
            if (oldRole.getBranchId() == null) continue;

            RoleEntity newRole = new RoleEntity();
            newRole.setName(oldRole.getName());
            newRole.setDescription(oldRole.getDescription());
            newRole.setStatus(oldRole.getStatus());
            newRole.setTenantId(tenantId);
            newRole.setBranchId(branchId);
            newRole.setFeatures(new HashSet<>(oldRole.getFeatures()));
            roleRepo.save(newRole);
        }
    }
}
