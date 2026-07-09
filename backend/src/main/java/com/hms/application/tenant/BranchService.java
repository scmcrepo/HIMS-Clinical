package com.hms.application.tenant;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.shared.FeatureEntity;
import com.hms.infrastructure.persistence.shared.FeatureJpaRepository;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.security.FeaturePermissionCacheService;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final FeatureJpaRepository featureRepo;
    private final UserJpaRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final FeaturePermissionCacheService permissionCache;
    private final jakarta.persistence.EntityManager entityManager;

    /** Standard branch-scoped roles and their default feature grants. */
    private static final Map<String, List<String>> BRANCH_ROLE_GRANTS = Map.of(
        "ADMIN", List.of(),  // Gets all features
        "RECEPTION", List.of("REGISTRATION", "APPOINTMENT", "OUT_PATIENT", "IN_PATIENT",
                             "OP_QUEUE", "ADMISSION_REQUEST", "OP_BILLING", "IP_BILLING"),
        "DOCTOR", List.of("OUT_PATIENT", "IN_PATIENT", "APPOINTMENT", "LAB_REPORT", "RADIOLOGY", "MEDICAL_RECORD",
                           "OP_QUEUE", "ADMISSION_REQUEST", "SETTINGS_FAVORITES"),
        "PHARMACIST", List.of("INVENTORY", "INVENTORY_GRN", "PURCHASE_ORDER",
                              "PHARMACY_SALES", "PHARMACY_SALES_HISTORY",
                              "PRESCRIBED_ORDERS", "SALES_RETURN",
                              "INVENTORY_GOODS_RETURN", "STOCK_ADJUSTMENT"),
        "BILLING", List.of("OP_BILLING", "IP_BILLING", "PETTY_CASH"),
        "NURSE", List.of("NURSE_OP_QUEUE", "NURSE_IN_PATIENT"),
        "BRANCH_ADMIN", List.of("REGISTRATION", "APPOINTMENT", "OUT_PATIENT", "IN_PATIENT", "INVENTORY",
                                "OP_QUEUE", "ADMISSION_REQUEST", "OP_BILLING", "IP_BILLING",
                                "PHARMACY_SALES", "PHARMACY_SALES_HISTORY", "PRESCRIBED_ORDERS",
                                "MEDICAL_RECORD")
    );

    /** Roles that receive all features for the branch. */
    private static final Set<String> FULL_ACCESS_BRANCH_ROLES = Set.of("ADMIN", "BRANCH_ADMIN");

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
        return create(code, name, address, contactNumber, null, null);
    }

    /**
     * Create a new branch, optionally provisioning a BRANCH_ADMIN user in one call.
     * This mirrors the hospital onboarding flow where a Hospital Admin is created alongside the tenant.
     */
    @Transactional
    public BranchEntity create(String code, String name, String address, String contactNumber,
                               String adminUsername, String adminPassword) {
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
        cloneDiagnosticTemplatesToBranch(tenantId, saved.getId());
        cloneRolesToBranch(tenantId, saved.getId());

        // Provision branch admin if credentials were supplied
        if (adminUsername != null && !adminUsername.isBlank()
                && adminPassword != null && !adminPassword.isBlank()) {
            provisionBranchAdmin(tenantId, saved.getId(), adminUsername, adminPassword);
        }

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

    @SuppressWarnings("unchecked")
    private void cloneDiagnosticTemplatesToBranch(UUID tenantId, UUID branchId) {
        // Find default branch of current tenant
        UUID defaultBranchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
            .map(BranchEntity::getId)
            .orElse(null);

        if (defaultBranchId == null) return;

        // 1. Clone Specimens from default branch to new branch
        List<Object[]> defaultSpecimens = entityManager.createNativeQuery(
            "SELECT id, name, description FROM specimens " +
            "WHERE tenant_id = :tenantId AND branch_id = :defaultBranchId")
            .setParameter("tenantId", tenantId)
            .setParameter("defaultBranchId", defaultBranchId)
            .getResultList();

        Map<UUID, UUID> specimenIdMap = new java.util.HashMap<>();

        for (Object[] row : defaultSpecimens) {
            UUID oldId = UUID.fromString(row[0].toString());
            String name = (String) row[1];
            String description = (String) row[2];

            // Check if specimen already exists in the new branch
            List<UUID> existingIds = entityManager.createNativeQuery(
                "SELECT id FROM specimens WHERE tenant_id = :tenantId AND branch_id = :branchId AND name = :name")
                .setParameter("tenantId", tenantId)
                .setParameter("branchId", branchId)
                .setParameter("name", name)
                .getResultList();

            UUID newSpecimenId;
            if (!existingIds.isEmpty()) {
                newSpecimenId = existingIds.get(0);
            } else {
                newSpecimenId = UUID.randomUUID();
                entityManager.createNativeQuery(
                    "INSERT INTO specimens (id, tenant_id, branch_id, name, description, status, created_at, modified_at) " +
                    "VALUES (:id, :tenantId, :branchId, :name, :description, 1, NOW(), NOW())")
                    .setParameter("id", newSpecimenId)
                    .setParameter("tenantId", tenantId)
                    .setParameter("branchId", branchId)
                    .setParameter("name", name)
                    .setParameter("description", description)
                    .executeUpdate();
            }
            specimenIdMap.put(oldId, newSpecimenId);
        }

        // 2. Clone Diagnostic Templates
        List<Object[]> defaultTemplates = entityManager.createNativeQuery(
            "SELECT id, name, diagnostic_type, format, specimen_id, department_id, order_number, " +
            "header, method, reference_range, unit, lab_template_type, template_html, status " +
            "FROM diagnostic_templates " +
            "WHERE tenant_id = :tenantId AND branch_id = :defaultBranchId")
            .setParameter("tenantId", tenantId)
            .setParameter("defaultBranchId", defaultBranchId)
            .getResultList();

        for (Object[] row : defaultTemplates) {
            UUID oldId = UUID.fromString(row[0].toString());
            String name = (String) row[1];
            int diagType = ((Number) row[2]).intValue();
            String format = (String) row[3];
            UUID oldSpecimenId = row[4] != null ? UUID.fromString(row[4].toString()) : null;
            UUID departmentId = row[5] != null ? UUID.fromString(row[5].toString()) : null;
            int orderNumber = row[6] != null ? ((Number) row[6]).intValue() : 0;
            String header = (String) row[7];
            String method = (String) row[8];
            String refRange = (String) row[9];
            String unit = (String) row[10];
            String labTemplateType = (String) row[11];
            String tempHtml = (String) row[12];
            int status = ((Number) row[13]).intValue();

            // Check if template already exists
            boolean exists = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM diagnostic_templates WHERE tenant_id = :tenantId AND branch_id = :branchId AND name = :name")
                .setParameter("tenantId", tenantId)
                .setParameter("branchId", branchId)
                .setParameter("name", name)
                .getSingleResult()).intValue() > 0;

            if (!exists) {
                UUID newTemplateId = UUID.randomUUID();
                UUID newSpecimenId = oldSpecimenId != null ? specimenIdMap.get(oldSpecimenId) : null;

                // Find corresponding active ServiceCatalogItem ID in new branch
                List<UUID> matchingSciIds = entityManager.createNativeQuery(
                    "SELECT id FROM service_catalog_items WHERE tenant_id = :tenantId AND (branch_id = :branchId OR branch_id IS NULL) " +
                    "AND UPPER(TRIM(name)) = UPPER(TRIM(:name)) AND status = 1 LIMIT 1")
                    .setParameter("tenantId", tenantId)
                    .setParameter("branchId", branchId)
                    .setParameter("name", name)
                    .getResultList();

                UUID chargeId = matchingSciIds.isEmpty() ? null : matchingSciIds.get(0);

                entityManager.createNativeQuery(
                    "INSERT INTO diagnostic_templates (id, tenant_id, branch_id, name, diagnostic_type, format, charge_id, specimen_id, department_id, order_number, " +
                    "header, method, reference_range, unit, lab_template_type, template_html, status, created_at, modified_at) " +
                    "VALUES (:id, :tenantId, :branchId, :name, :diagType, :format, :chargeId, :specimenId, :deptId, :orderNum, " +
                    ":header, :method, :refRange, :unit, :labTemplateType, :tempHtml, :status, NOW(), NOW())")
                    .setParameter("id", newTemplateId)
                    .setParameter("tenantId", tenantId)
                    .setParameter("branchId", branchId)
                    .setParameter("name", name)
                    .setParameter("diagType", diagType)
                    .setParameter("format", format)
                    .setParameter("chargeId", chargeId)
                    .setParameter("specimenId", newSpecimenId)
                    .setParameter("deptId", departmentId)
                    .setParameter("orderNum", orderNumber)
                    .setParameter("header", header)
                    .setParameter("method", method)
                    .setParameter("refRange", refRange)
                    .setParameter("unit", unit)
                    .setParameter("labTemplateType", labTemplateType)
                    .setParameter("tempHtml", tempHtml)
                    .setParameter("status", status)
                    .executeUpdate();

                // Clone diagnostic_template_lab_template join rows
                entityManager.createNativeQuery(
                    "INSERT INTO diagnostic_template_lab_template (diagnostic_template_id, lab_template_detail_id) " +
                    "SELECT :newId, lab_template_detail_id FROM diagnostic_template_lab_template " +
                    "WHERE diagnostic_template_id = :oldId")
                    .setParameter("newId", newTemplateId)
                    .setParameter("oldId", oldId)
                    .executeUpdate();
            }
        }
    }

    private void cloneRolesToBranch(UUID tenantId, UUID branchId) {
        // Try cloning from the default branch first
        UUID defaultBranchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
            .map(BranchEntity::getId)
            .orElse(null);

        if (defaultBranchId != null) {
            List<RoleEntity> defaultRoles = roleRepo.findAllActiveWithFeaturesByTenantAndBranch(tenantId, defaultBranchId);
            for (RoleEntity oldRole : defaultRoles) {
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
        } else {
            // No default branch — create standard branch-scoped roles from the feature catalogue
            List<FeatureEntity> allFeatures = featureRepo.findAllByTenantId(tenantId);
            java.util.Map<String, FeatureEntity> featuresByKey = new java.util.HashMap<>();
            for (FeatureEntity fe : allFeatures) {
                featuresByKey.put(fe.getFeatureKey(), fe);
            }

            for (var entry : BRANCH_ROLE_GRANTS.entrySet()) {
                String roleName = entry.getKey();
                List<String> grantKeys = entry.getValue();

                // Skip if this role already exists for this branch
                if (roleRepo.findByNameAndTenantIdAndBranchId(roleName, tenantId, branchId).isPresent()) {
                    continue;
                }

                RoleEntity role = new RoleEntity();
                role.setName(roleName);
                role.setDescription(roleName + " (seeded)");
                role.setStatus((short) 1);
                role.setTenantId(tenantId);
                role.setBranchId(branchId);

                Set<FeatureEntity> grants = new HashSet<>();
                if (FULL_ACCESS_BRANCH_ROLES.contains(roleName)) {
                    grants.addAll(allFeatures);
                } else {
                    for (String key : grantKeys) {
                        FeatureEntity fe = featuresByKey.get(key);
                        if (fe != null) grants.add(fe);
                    }
                }
                role.setFeatures(grants);
                roleRepo.save(role);
            }
        }
    }

    /**
     * Create a BRANCH_ADMIN user scoped to a specific branch.
     * Similar to TenantService.provisionHospitalAdmin() but branch-scoped.
     */
    @Transactional
    public UserEntity provisionBranchAdmin(UUID tenantId, UUID branchId, String username, String rawPassword) {
        String clean = username == null ? "" : username.toLowerCase().trim();
        if (clean.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessRuleViolationException("Branch admin username and password are required");
        }
        if (userRepo.existsByUsername(clean)) {
            throw new BusinessRuleViolationException("Username '" + username + "' already exists");
        }
        RoleEntity branchAdmin = roleRepo.findByNameAndTenantIdAndBranchId("BRANCH_ADMIN", tenantId, branchId)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "BRANCH_ADMIN role not found for this branch; roles may not have been seeded"));

        UserEntity admin = new UserEntity();
        admin.setUsername(clean);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setFirstName("Branch");
        admin.setLastName("Admin");
        admin.setStatus((short) 1);
        admin.setAccountLocked(false);
        admin.setSpeechLanguage("en-IN");
        admin.setTextAutoSuggest(true);
        admin.setShowCasesheet(false);
        admin.setCreatedAt(Instant.now());
        admin.setModifiedAt(Instant.now());
        admin.setTenantId(tenantId);
        admin.setBranchId(branchId);
        admin.setRoles(new HashSet<>(java.util.Set.of(branchAdmin)));

        UserEntity saved = userRepo.save(admin);
        permissionCache.rebuildCacheForTenant(tenantId);
        return saved;
    }
}
