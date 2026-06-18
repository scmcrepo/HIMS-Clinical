package com.hms.application.tenant;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.shared.FeatureEntity;
import com.hms.infrastructure.persistence.shared.FeatureJpaRepository;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.infrastructure.persistence.shared.UserEntity;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import com.hms.security.FeaturePermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Platform-level tenant management. Callers are SUPERADMIN only (enforced at the controller).
 *
 * <p>{@link #seedRbac(UUID)} reproduces the intent of {@code V088__seed_full_rbac.sql} and
 * {@code V089__seed_default_role_grants.sql}, but scoped to a single tenant — so a brand new
 * hospital comes online with the standard feature catalogue, the standard roles, and sensible
 * default grants, all editable afterwards from Settings → Roles &amp; Permissions.
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantJpaRepository tenantRepo;
    private final BranchJpaRepository branchRepo;
    private final RoleJpaRepository roleRepo;
    private final FeatureJpaRepository featureRepo;
    private final FeaturePermissionCacheService permissionCache;
    private final UserJpaRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final jakarta.persistence.EntityManager entityManager;

    private static final List<String[]> FEATURES = List.of(
        new String[]{"ADMISSION_REQUEST", "CLINICAL", "Admission Request"},
        new String[]{"APPOINTMENT", "CLINICAL", "Appointment"},
        new String[]{"BEDMANAGEMENT", "CLINICAL", "Bedmanagement"},
        new String[]{"DATA_IMPORT", "SETTINGS", "Data Import"},
        new String[]{"INSURANCE", "INSURANCE", "Insurance"},
        new String[]{"INVENTORY", "INVENTORY", "Inventory"},
        new String[]{"INVENTORY_GOODS_RETURN", "INVENTORY", "Inventory Goods Return"},
        new String[]{"INVENTORY_GRN", "INVENTORY", "Inventory Grn"},
        new String[]{"IN_PATIENT", "CLINICAL", "In Patient"},
        new String[]{"IP_BILLING", "BILLING", "IP Billing"},
        new String[]{"LAB_REPORT", "DIAGNOSTICS", "Lab Report"},
        new String[]{"MARKETING", "MARKETING", "Marketing"},
        new String[]{"MEDICAL_RECORD", "MRD", "Medical Record"},
        new String[]{"NURSE_IN_PATIENT", "CLINICAL", "Access nurse inpatient list"},
        new String[]{"NURSE_OP_QUEUE", "CLINICAL", "Access nurse outpatient queue"},
        new String[]{"OP_BILLING", "BILLING", "OP Billing"},
        new String[]{"OP_QUEUE", "CLINICAL", "OP Queue"},
        new String[]{"OT_SCHEDULE", "OTSCHEDULE", "Ot Schedule"},
        new String[]{"OUT_PATIENT", "CLINICAL", "Out Patient"},
        new String[]{"PETTY_CASH", "BILLING", "Petty Cash Billing"},
        new String[]{"PHARMACY_SALES", "INVENTORY", "Pharmacy Sales"},
        new String[]{"PHARMACY_SALES_HISTORY", "INVENTORY", "Pharmacy Sales History"},
        new String[]{"PRESCRIBED_ORDERS", "BILLING", "Prescribed Orders"},
        new String[]{"PURCHASE_ORDER", "INVENTORY", "Purchase Order"},
        new String[]{"RADIOLOGY", "DIAGNOSTICS", "Radiology"},
        new String[]{"REGISTRATION", "CLINICAL", "Registration"},
        new String[]{"REPORT_BILLING", "REPORTS", "Report Billing"},
        new String[]{"REPORT_COLLECTION", "REPORTS", "Report Collection"},
        new String[]{"REPORT_DIAGNOSTICS", "REPORTS", "Report Diagnostics"},
        new String[]{"REPORT_ENCOUNTER", "REPORTS", "Report Encounter"},
        new String[]{"REPORT_INPATIENT", "REPORTS", "Report Inpatient"},
        new String[]{"REPORT_INVENTORY", "REPORTS", "Report Inventory"},
        new String[]{"REPORT_PHARMACY", "REPORTS", "Report Pharmacy"},
        new String[]{"REPORT_PROCUREMENT", "REPORTS", "Report Procurement"},
        new String[]{"REPORT_REVENUE", "REPORTS", "Report Revenue"},
        new String[]{"SALES_RETURN", "PHARMACY", "Sales Return"},
        new String[]{"SETTINGS_BED", "SETTINGS", "Settings Bed"},
        new String[]{"SETTINGS_BEDTYPE", "SETTINGS", "Settings Bedtype"},
        new String[]{"SETTINGS_CASESHEET_TEMPLATE", "SETTINGS", "Settings Casesheet Template"},
        new String[]{"SETTINGS_CATEGORY", "SETTINGS", "Settings Category"},
        new String[]{"SETTINGS_CHARGES", "SETTINGS", "Settings Charges"},
        new String[]{"SETTINGS_CONFIGURATION", "SETTINGS", "Settings Configuration"},
        new String[]{"SETTINGS_CONSULTANT", "SETTINGS", "Settings Consultant"},
        new String[]{"SETTINGS_DEPARTMENT", "SETTINGS", "Settings Department"},
        new String[]{"SETTINGS_DISCHARGE_TEMPLATE", "SETTINGS", "Settings Discharge Template"},
        new String[]{"SETTINGS_FAVORITES", "SETTINGS", "Settings Favorites"},
        new String[]{"SETTINGS_FREQUENCY", "SETTINGS", "Settings Frequency"},
        new String[]{"SETTINGS_HOSPITALPROFILE", "SETTINGS", "Settings Hospitalprofile"},
        new String[]{"SETTINGS_ITEM", "SETTINGS", "Settings Item"},
        new String[]{"SETTINGS_ORDERSET", "SETTINGS", "Settings Orderset"},
        new String[]{"SETTINGS_PAYERTYPE", "SETTINGS", "Settings Payertype"},
        new String[]{"SETTINGS_PREFIX", "SETTINGS", "Settings Prefix"},
        new String[]{"SETTINGS_PRINT_TEMPLATE", "SETTINGS", "Settings Print Template"},
        new String[]{"SETTINGS_RESULT_TEMPLATE", "SETTINGS", "Settings Result Template"},
        new String[]{"SETTINGS_ROLE", "SETTINGS", "Settings Role"},
        new String[]{"SETTINGS_SCHEDULEDDRUG", "SETTINGS", "Settings Scheduled Drug"},
        new String[]{"SETTINGS_SPECIMEN", "SETTINGS", "Settings Specimen"},
        new String[]{"SETTINGS_STAFF", "SETTINGS", "Settings Staff"},
        new String[]{"SETTINGS_SUPPLIER", "SETTINGS", "Settings Supplier"},
        new String[]{"SETTINGS_TAX", "SETTINGS", "Settings Tax"},
        new String[]{"SETTINGS_USERS", "SETTINGS", "Settings Users"},
        new String[]{"STOCK_ADJUSTMENT", "INVENTORY", "Stock Adjustment"}
    );

    // Default role -> feature grants (mirror of V089).
    private static final Map<String, List<String>> ROLE_GRANTS = Map.of(
        "ADMIN", List.of(),  // ADMIN gets ALL features (handled specially below)
        "RECEPTION", List.of("REGISTRATION", "APPOINTMENT", "OUT_PATIENT", "IN_PATIENT",
                             "OP_QUEUE", "ADMISSION_REQUEST", "OP_BILLING", "IP_BILLING"),
        "DOCTOR", List.of("OUT_PATIENT", "IN_PATIENT", "APPOINTMENT", "LAB_REPORT", "RADIOLOGY", "MEDICAL_RECORD",
                           "OP_QUEUE", "ADMISSION_REQUEST", "SETTINGS_FAVORITES"),
        "PHARMACIST", List.of("INVENTORY", "INVENTORY_GRN", "PURCHASE_ORDER",
                              "PHARMACY_SALES", "PHARMACY_SALES_HISTORY"),
        "BILLING", List.of("OP_BILLING", "IP_BILLING", "PETTY_CASH"),
        "NURSE", List.of("NURSE_OP_QUEUE", "NURSE_IN_PATIENT"),
        // Tenant hierarchy admins. HOSPITAL_ADMIN gets ALL features (handled specially below,
        // like ADMIN). BRANCH_ADMIN gets a broad branch-level operational set.
        "HOSPITAL_ADMIN", List.of(),
        "BRANCH_ADMIN", List.of("REGISTRATION", "APPOINTMENT", "OUT_PATIENT", "IN_PATIENT", "INVENTORY",
                                "OP_QUEUE", "ADMISSION_REQUEST", "OP_BILLING", "IP_BILLING",
                                "PHARMACY_SALES", "PHARMACY_SALES_HISTORY", "PRESCRIBED_ORDERS")
    );

    /** Roles that should receive the full feature catalogue. */
    private static final Set<String> FULL_ACCESS_ROLES = Set.of("ADMIN", "HOSPITAL_ADMIN");

    @Transactional(readOnly = true)
    public List<TenantEntity> listAll() {
        return tenantRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<TenantEntity> listActivePublic() {
        return tenantRepo.findAllByStatus((short) 1);
    }

    @Transactional(readOnly = true)
    public TenantEntity get(UUID tenantId) {
        return tenantRepo.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }

    @Transactional
    public TenantEntity create(String slug, String name, String description, String address, String contactNumber) {
        String normalized = slug == null ? "" : slug.trim().toLowerCase();
        if (normalized.isBlank()) throw new BusinessRuleViolationException("slug is required");
        if (tenantRepo.existsBySlug(normalized)) {
            throw new BusinessRuleViolationException("Tenant slug '" + normalized + "' already exists");
        }
        TenantEntity t = new TenantEntity();
        t.setSlug(normalized);
        t.setName(name);
        t.setDescription(description);
        t.setAddress(address);
        t.setContactNumber(contactNumber);
        t.setStatus((short) 1);
        TenantEntity saved = tenantRepo.save(t);

        // Every tenant comes online with one default branch (the platform hierarchy:
        // "Default Branch (Created Automatically)").
        createDefaultBranch(saved.getId(), name, address, contactNumber);
        return saved;
    }

    /** Create the auto-default branch for a tenant if it has none. Idempotent. */
    @Transactional
    public BranchEntity createDefaultBranch(UUID tenantId, String name, String address, String contactNumber) {
        return branchRepo.findByTenantIdAndIsDefaultTrue(tenantId).orElseGet(() -> {
            BranchEntity b = new BranchEntity();
            b.setTenantId(tenantId);
            b.setCode("MAIN");
            b.setName(name != null ? name + " - Main Branch" : "Main Branch");
            b.setAddress(address);
            b.setContactNumber(contactNumber);
            b.setDefault(true);
            b.setStatus((short) 1);
            return branchRepo.save(b);
        });
    }

    /**
     * Single onboarding flow (audit 17.5 / Critical Finding 4):
     *   Super Admin -> create hospital -> create default branch -> seed RBAC -> create Hospital Admin.
     * Guarantees every hospital has at least one Hospital Admin after onboarding.
     */
    @Transactional
    public TenantEntity onboard(String slug, String name, String description, String address, String contactNumber, String adminUsername, String adminPassword,
                                String adminFirstName, String adminLastName) {
        TenantEntity tenant = create(slug, name, description, address, contactNumber);   // tenant + auto default branch
        seedRbac(tenant.getId());                    // features + roles (incl. HOSPITAL_ADMIN)
        if (adminUsername != null && !adminUsername.isBlank()) {
            provisionHospitalAdmin(tenant.getId(), adminUsername, adminPassword,
                                   adminFirstName, adminLastName);
        }
        return tenant;
    }

    /** Create a tenant-wide HOSPITAL_ADMIN login for a tenant (branchless, all-branches access). */
    @Transactional
    public UserEntity provisionHospitalAdmin(UUID tenantId, String username, String rawPassword,
                                             String firstName, String lastName) {
        String clean = username == null ? "" : username.toLowerCase().trim();
        if (clean.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessRuleViolationException("Hospital admin username and password are required");
        }
        if (userRepo.existsByUsername(clean)) {
            throw new BusinessRuleViolationException("Username '" + username + "' already exists");
        }
        RoleEntity hospitalAdmin = roleRepo.findByNameAndTenantId("HOSPITAL_ADMIN", tenantId)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "HOSPITAL_ADMIN role not found for tenant; seed RBAC first"));

        UserEntity admin = new UserEntity();
        admin.setUsername(clean);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setFirstName(firstName != null && !firstName.isBlank() ? firstName : "Hospital");
        admin.setLastName(lastName != null && !lastName.isBlank() ? lastName : "Admin");
        admin.setStatus((short) 1);
        admin.setAccountLocked(false);
        admin.setSpeechLanguage("en-IN");
        admin.setTextAutoSuggest(true);
        admin.setShowCasesheet(false);
        admin.setCreatedAt(Instant.now());
        admin.setModifiedAt(Instant.now());
        admin.setTenantId(tenantId);   // belongs to the tenant
        admin.setBranchId(null);       // tenant-wide: spans all branches
        admin.setRoles(new HashSet<>(java.util.Set.of(hospitalAdmin)));

        UserEntity saved = userRepo.save(admin);
        permissionCache.rebuildCacheForTenant(tenantId);
        return saved;
    }

    @Transactional
    public void resetAdminPassword(UUID tenantId, String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessRuleViolationException("Password is required");
        }
        List<UserEntity> users = userRepo.findAllByTenantId(tenantId);
        UserEntity admin = users.stream()
            .filter(u -> u.getRoles().stream().anyMatch(r -> "HOSPITAL_ADMIN".equalsIgnoreCase(r.getName())))
            .findFirst()
            .orElseThrow(() -> new BusinessRuleViolationException("Hospital admin not found for this tenant"));
        
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setModifiedAt(Instant.now());
        userRepo.save(admin);
    }

    @Transactional
    public TenantEntity update(UUID tenantId, String name, String description, String address, String contactNumber, Short status) {
        TenantEntity t = get(tenantId);
        if (name != null && !name.isBlank()) t.setName(name);
        if (description != null) t.setDescription(description);
        if (address != null) t.setAddress(address);
        if (contactNumber != null) t.setContactNumber(contactNumber);
        if (status != null) t.setStatus(status);
        return tenantRepo.save(t);
    }

    /** Seed the standard feature catalogue, roles, and default grants for a tenant. Idempotent. */
    @Transactional
    public void seedRbac(UUID tenantId) {
        TenantEntity tenant = get(tenantId);

        // 1. Features (skip if a key already exists for this tenant).
        Map<String, FeatureEntity> featuresByKey = new HashMap<>();
        for (String[] f : FEATURES) {
            FeatureEntity feature = featureRepo.findByFeatureKeyAndTenantId(f[0], tenantId)
                .orElseGet(() -> {
                    FeatureEntity fe = new FeatureEntity();
                    fe.setFeatureKey(f[0]);
                    fe.setModule(f[1]);
                    fe.setDescription(f[2]);
                    fe.setTenantId(tenantId);
                    return featureRepo.save(fe);
                });
            featuresByKey.put(f[0], feature);
        }

        // 2. Standard roles + SUPERADMIN-less defaults (SUPERADMIN is platform-level, not per-tenant).
        Set<String> allRoleNames = new HashSet<>(ROLE_GRANTS.keySet());
        for (String roleName : allRoleNames) {
            RoleEntity role = roleRepo.findByNameAndTenantId(roleName, tenantId)
                .orElseGet(() -> {
                    RoleEntity re = new RoleEntity();
                    re.setName(roleName);
                    re.setDescription(roleName + " (seeded)");
                    re.setStatus((short) 1);
                    re.setTenantId(tenantId);
                    return re;
                });

            Set<FeatureEntity> grants = new HashSet<>(role.getFeatures());
            if (FULL_ACCESS_ROLES.contains(roleName)) {
                grants.addAll(featuresByKey.values()); // ADMIN gets everything
            } else {
                for (String key : ROLE_GRANTS.getOrDefault(roleName, List.of())) {
                    FeatureEntity fe = featuresByKey.get(key);
                    if (fe != null) grants.add(fe);
                }
            }
            role.setFeatures(grants);
            roleRepo.save(role);
        }

        permissionCache.rebuildCacheForTenant(tenantId);

        // 3. Seed default print templates if none exist for this tenant.
        long templateCount = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM print_templates WHERE tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult()).longValue();
        if (templateCount == 0) {
            entityManager.createNativeQuery(
                "INSERT INTO print_templates (" +
                "    id, name, document_type, print_mode, height, width, " +
                "    margin_top, margin_bottom, margin_left, margin_right, " +
                "    margin, page_size, pug_template, content, default_printer, " +
                "    is_default, status, tenant_id, branch_id" +
                ") " +
                "SELECT " +
                "    gen_random_uuid(), name, document_type, print_mode, height, width, " +
                "    margin_top, margin_bottom, margin_left, margin_right, " +
                "    margin, page_size, pug_template, content, default_printer, " +
                "    is_default, status, :tenantId, null " +
                "FROM print_templates " +
                "WHERE tenant_id = '00000000-0000-0000-0000-000000000001'")
                .setParameter("tenantId", tenantId)
                .executeUpdate();
        }

        // 4. Seed default frequencies if none exist for this tenant.
        long frequencyCount = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM frequencies WHERE tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult()).longValue();
        if (frequencyCount == 0) {
            entityManager.createNativeQuery(
                "INSERT INTO frequencies (id, name, value, status, created_at, modified_at, tenant_id, branch_id) " +
                "SELECT gen_random_uuid(), name, value, status, NOW(), NOW(), :tenantId, null " +
                "FROM frequencies " +
                "WHERE tenant_id = '00000000-0000-0000-0000-000000000001'")
                .setParameter("tenantId", tenantId)
                .executeUpdate();
        }

        // 5. Seed default scheduled drugs if none exist for this tenant.
        long scheduledDrugCount = ((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM scheduled_drugs WHERE tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult()).longValue();
        if (scheduledDrugCount == 0) {
            entityManager.createNativeQuery(
                "INSERT INTO scheduled_drugs (id, name, status, created_at, modified_at, tenant_id, branch_id) " +
                "SELECT gen_random_uuid(), name, status, NOW(), NOW(), :tenantId, null " +
                "FROM scheduled_drugs " +
                "WHERE tenant_id = '00000000-0000-0000-0000-000000000001'")
                .setParameter("tenantId", tenantId)
                .executeUpdate();
        }
    }
}
