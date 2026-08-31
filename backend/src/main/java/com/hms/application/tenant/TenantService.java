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
        new String[]{"INSURANCE_REPORTS", "INSURANCE", "Insurance Reports"},
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
        // WO-021. Seeded for existing tenants by V199; without this line a
        // hospital onboarded next month gets the insurance desk but no
        // insurance reports, and the failure is a silent 403.
        new String[]{"REPORT_INSURANCE", "REPORTS", "Report Insurance"},
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
        new String[]{"SETTINGS_SMTP", "SETTINGS", "SMTP Configuration"},
        new String[]{"SETTINGS_SPECIMEN", "SETTINGS", "Settings Specimen"},
        new String[]{"SETTINGS_STAFF", "SETTINGS", "Settings Staff"},
        new String[]{"SETTINGS_SUPPLIER", "SETTINGS", "Settings Supplier"},
        new String[]{"SETTINGS_TAX", "SETTINGS", "Settings Tax"},
        new String[]{"SETTINGS_TEMPLATE", "SETTINGS", "Manage clinical templates"},
        new String[]{"SETTINGS_USERS", "SETTINGS", "Settings Users"},
        new String[]{"STOCK_ADJUSTMENT", "INVENTORY", "Stock Adjustment"},
        // ── Agent gateway (WO-001/T-003). Seeded in V176 for tenants that already
        // existed; listed here so tenants provisioned from now on get them too.
        // Seeding only in the migration is the classic failure mode in this
        // codebase: works in dev, silently 403s for the next hospital onboarded.
        new String[]{"AGENT_SCHEDULING_READ", "AGENT", "Agent: read appointment slot availability"},
        new String[]{"AGENT_SCHEDULING_WRITE", "AGENT", "Agent: book and modify appointments"},
        new String[]{"AGENT_BILLING_READ", "AGENT", "Agent: read patient billing ledger"},
        new String[]{"AGENT_BED_READ", "AGENT", "Agent: read bed occupancy"},
        new String[]{"AGENT_TOOLS_READ", "AGENT", "Agent: read tool schema catalogue"},
        new String[]{"AGENT_TOKEN_MANAGE", "AGENT", "Manage agent API tokens"},
        new String[]{"HITL_MANAGE", "AGENT", "Review and resolve escalated agent conversations"},
        new String[]{"AGENT_HITL_RAISE", "AGENT", "Agent: escalate a conversation to a human"},
        new String[]{"ABHA_MANAGE", "ABDM", "Create and link patient ABHA identities"},
        new String[]{"NHCX_CLAIMS", "CLAIM", "Submit and track NHCX claims"},
        new String[]{"AGENT_ABHA_WRITE", "AGENT", "Agent: initiate ABHA linkage"},
        new String[]{"AGENT_CLAIMS_READ", "AGENT", "Agent: read claim and eligibility status"},
        new String[]{"CONSENT_MANAGE", "COMPLIANCE", "Capture and withdraw patient consent"},
        // WO-023. Split from CONSENT_MANAGE and granted wide: any clinician
        // about to send an automated reminder needs to check whether they
        // may, and that read should not require the ability to record
        // agreement on the patient's behalf.
        new String[]{"CONSENT_VIEW", "COMPLIANCE", "View a patient's consent record and history"},
        new String[]{"ERASURE_MANAGE", "COMPLIANCE", "Process erasure and correction requests"},
        // WO-024. Split from ERASURE_MANAGE because taking a request and
        // acting on one are different risks: reception must be able to
        // record that a patient asked to be forgotten without also being
        // able to verify identity and trigger an irreversible sweep.
        new String[]{"ERASURE_REQUEST", "COMPLIANCE", "Raise a data-principal erasure or correction request"},
        // WO-026. Split three ways because the acts differ in consequence:
        // RAISE is wide (a near-miss nobody can file is one nobody learns
        // from), NOTIFY is narrowest (telling the Board is irreversible).
        new String[]{"INCIDENT_RAISE", "COMPLIANCE", "Report a suspected security or data incident"},
        new String[]{"INCIDENT_MANAGE", "COMPLIANCE", "Triage, contain and close security incidents"},
        new String[]{"INCIDENT_NOTIFY", "COMPLIANCE", "Notify the Data Protection Board and affected individuals"},
        // WO-027. GRIEVANCE_RAISE is wide for the same reason INCIDENT_RAISE
        // is: a complaint only an administrator can log is one that gets
        // talked out of existence at the desk.
        new String[]{"GRIEVANCE_RAISE", "COMPLIANCE", "Record a data protection grievance from a patient"},
        new String[]{"GRIEVANCE_MANAGE", "COMPLIANCE", "Work, resolve and close grievances"},
        new String[]{"COMPLIANCE_CONTACT_MANAGE", "COMPLIANCE", "Maintain the published data protection contact and DPO"},
        // WO-025. Narrow: changing a retention period changes when patient
        // records are destroyed, which is closer to a legal act than an
        // administrative one.
        new String[]{"RETENTION_MANAGE", "COMPLIANCE", "View and configure data retention policies"},
        new String[]{"ROLLOUT_MANAGE", "COMPLIANCE", "Control agent rollout stage and kill switch"},
        // WO-017 / PT-001. Two keys, because the split is a security boundary:
        // PORTAL_IDENTITY means "proved possession of this mobile number" and
        // reads no clinical data; PORTAL_PATIENT means "is this patient, at
        // this hospital, at this branch". One key would let a client swap the
        // patientId after verification and read a sibling's records.
        new String[]{"PORTAL_IDENTITY", "PORTAL", "Patient portal: mobile number verified, pre-selection scope"},
        new String[]{"PORTAL_PATIENT", "PORTAL", "Patient portal: authenticated patient, own records only"}
    );

    // Default role -> feature grants (mirror of V089).
    private static final Map<String, List<String>> ROLE_GRANTS = Map.ofEntries(
        // ADMIN gets ALL features; handled specially below via FULL_ACCESS_ROLES.
        Map.entry("ADMIN", List.of()),
        Map.entry("RECEPTION", List.of("REGISTRATION", "APPOINTMENT", "OUT_PATIENT", "IN_PATIENT",
                             "OP_QUEUE", "ADMISSION_REQUEST", "OP_BILLING", "IP_BILLING",
                             // The Copilot queue is front-desk work: a receptionist
                             // must be able to take over an escalated conversation
                             // without waiting for a manager.
                             "HITL_MANAGE", "ABHA_MANAGE", "NHCX_CLAIMS", "CONSENT_MANAGE")),

        Map.entry("DOCTOR", List.of("OUT_PATIENT", "IN_PATIENT", "APPOINTMENT", "LAB_REPORT", "RADIOLOGY", "MEDICAL_RECORD",
                           "OP_QUEUE", "ADMISSION_REQUEST", "SETTINGS_FAVORITES")),

        Map.entry("PHARMACIST", List.of("INVENTORY", "INVENTORY_GRN", "PURCHASE_ORDER",
                              "PHARMACY_SALES", "PHARMACY_SALES_HISTORY",
                              "PRESCRIBED_ORDERS", "SALES_RETURN",
                              "INVENTORY_GOODS_RETURN", "STOCK_ADJUSTMENT")),

        Map.entry("BILLING", List.of("OP_BILLING", "IP_BILLING", "PETTY_CASH")),

        Map.entry("NURSE", List.of("NURSE_OP_QUEUE", "NURSE_IN_PATIENT")),

        // Tenant hierarchy admins. HOSPITAL_ADMIN gets Reports + Settings admin features only;
        // they manage branches and view reports but don't do clinical/operational work.
        // BRANCH_ADMIN gets a broad branch-level operational set.
        Map.entry("HOSPITAL_ADMIN", List.of(
            "REPORT_ENCOUNTER", "REPORT_BILLING", "REPORT_COLLECTION", "REPORT_DIAGNOSTICS",
            "REPORT_REVENUE", "REPORT_INPATIENT", "REPORT_PROCUREMENT", "REPORT_INVENTORY",
            "REPORT_PHARMACY", "REPORT_INSURANCE", "INSURANCE", "INSURANCE_REPORTS",
            "SETTINGS_USERS", "SETTINGS_HOSPITALPROFILE", "SETTINGS_ROLE", "SETTINGS_SMTP",
            "SETTINGS_CONFIGURATION", "HITL_MANAGE", "ABHA_MANAGE", "NHCX_CLAIMS",
            "CONSENT_MANAGE", "ERASURE_MANAGE", "ROLLOUT_MANAGE",
            // Issuing and revoking agent credentials is an administrative act.
            // Deliberately NOT granted to the AGENT role: an agent must not be
            // able to mint itself a wider credential.
            "AGENT_TOKEN_MANAGE"
        )),

        Map.entry("BRANCH_ADMIN", List.of("REGISTRATION", "APPOINTMENT", "OUT_PATIENT", "IN_PATIENT", "INVENTORY",
                                "OP_QUEUE", "ADMISSION_REQUEST", "OP_BILLING", "IP_BILLING", "INSURANCE", "INSURANCE_REPORTS",
                                "PHARMACY_SALES", "PHARMACY_SALES_HISTORY", "PRESCRIBED_ORDERS",
                                "MEDICAL_RECORD", "SETTINGS_SMTP", "SETTINGS_TEMPLATE",
                                "HITL_MANAGE", "ABHA_MANAGE", "NHCX_CLAIMS", "CONSENT_MANAGE")),

        // The AI agent service principal. Operational tool access only — no
        // token management, no settings, no user administration.
        // Converted from Map.of() to Map.ofEntries() by WO-017/PT-001: adding
        // PORTAL_PATIENT made the 10th pair, which is Map.of()'s hard ceiling.
        // The agent may ask for help but never resolve its own request for it:
        // AGENT_HITL_RAISE yes, HITL_MANAGE no.
        Map.entry("AGENT", List.of("AGENT_SCHEDULING_READ", "AGENT_SCHEDULING_WRITE",
                         "AGENT_BILLING_READ", "AGENT_BED_READ", "AGENT_TOOLS_READ",
                         "AGENT_HITL_RAISE", "AGENT_ABHA_WRITE", "AGENT_CLAIMS_READ")),
        // WO-017 / PT-001. The patient portal principal. Exactly its two portal
        // keys and nothing else: a patient principal holding REGISTRATION or
        // MEDICAL_RECORD could read every patient in the tenant instead of only
        // themselves, because those features are scoped to staff, not to a row.
        Map.entry("PORTAL_PATIENT", List.of("PORTAL_IDENTITY", "PORTAL_PATIENT"))
    );

    /** Roles that should receive the full feature catalogue. */
    private static final Set<String> FULL_ACCESS_ROLES = Set.of("ADMIN");

    @Transactional(readOnly = true)
    public List<TenantEntity> listAll() {
        return tenantRepo.findAll().stream()
            .filter(t -> !t.getId().equals(UUID.fromString("00000000-0000-0000-0000-000000000001")))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TenantEntity> listActivePublic() {
        return tenantRepo.findAllByStatus((short) 1).stream()
            .filter(t -> !t.getId().equals(UUID.fromString("00000000-0000-0000-0000-000000000001")))
            .toList();
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

        // No default branch is created automatically.
        // The Hospital Admin creates branches explicitly from the Branch Management UI.
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
            BranchEntity saved = branchRepo.save(b);
            cloneTemplatesToBranch(tenantId, saved.getId());
            return saved;
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
        Optional<UserEntity> adminOpt = users.stream()
            .filter(u -> u.getRoles().stream().anyMatch(r -> "HOSPITAL_ADMIN".equalsIgnoreCase(r.getName())))
            .findFirst();

        if (adminOpt.isPresent()) {
            UserEntity admin = adminOpt.get();
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setModifiedAt(Instant.now());
            userRepo.save(admin);
        } else {
            // Automatically provision a hospital admin if none exists
            TenantEntity tenant = get(tenantId);
            String username = "default".equals(tenant.getSlug()) ? "admin" : tenant.getSlug() + "-admin";
            if (userRepo.existsByUsername(username)) {
                username = username + "-" + UUID.randomUUID().toString().substring(0, 4);
            }
            provisionHospitalAdmin(tenantId, username, password, "Hospital", "Admin");
        }
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
        //    Tenant-wide roles (HOSPITAL_ADMIN, ADMIN) get branchId=null.
        //    Branch-scoped roles (RECEPTION, DOCTOR, etc.) are only created when a branch
        //    is actually provisioned via BranchService.create(), so we skip them here if
        //    there is no default branch.
        UUID defaultBranchId = branchRepo.findByTenantIdAndIsDefaultTrue(tenantId)
            .map(BranchEntity::getId)
            .orElse(null);

        // AGENT is tenant-wide (branch_id NULL), matching V176. A token may be
        // pinned to a branch via agent_api_tokens.branch_id, but the role itself
        // spans the tenant — otherwise existing tenants (seeded by V176 with
        // branch_id NULL) and future tenants would get different role shapes.
        Set<String> tenantWideRoles = Set.of("ADMIN", "HOSPITAL_ADMIN", "AGENT", "PORTAL_PATIENT");

        Set<String> allRoleNames = new HashSet<>(ROLE_GRANTS.keySet());
        for (String roleName : allRoleNames) {
            boolean isTenantWide = tenantWideRoles.contains(roleName);
            UUID targetBranchId = isTenantWide ? null : defaultBranchId;

            // Skip branch-scoped roles when there is no default branch.
            // They will be created when branches are created via BranchService.
            if (!isTenantWide && defaultBranchId == null) continue;

            RoleEntity role = roleRepo.findByNameAndTenantIdAndBranchId(roleName, tenantId, targetBranchId)
                .orElseGet(() -> {
                    RoleEntity re = new RoleEntity();
                    re.setName(roleName);
                    re.setDescription(roleName + " (seeded)");
                    re.setStatus((short) 1);
                    re.setTenantId(tenantId);
                    re.setBranchId(targetBranchId);
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
}
