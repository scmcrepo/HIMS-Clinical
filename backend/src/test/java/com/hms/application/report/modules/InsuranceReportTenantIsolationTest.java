package com.hms.application.report.modules;

import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WO-021 / IR-001 — tenant isolation across all ten insurance reports.
 *
 * <h2>Why this test carries more weight than its size suggests</h2>
 * Every other business query in this codebase runs through Hibernate and
 * inherits the {@code tenantFilter}. These ten do not: they are raw
 * {@link JdbcTemplate} SQL, which bypasses the filters entirely. Isolation
 * exists only because each query remembers to append
 * {@code scope.predicate("i")} and {@code scope.args()}.
 *
 * <p>A query that forgets is not a slow report or a wrong total. It is one
 * hospital reading another hospital's claim values, sanctioned limits and
 * disallowance history — and in review it looks completely ordinary, because
 * the omission is the absence of two lines rather than the presence of a wrong
 * one. Nothing else in the build catches it.
 *
 * <h2>What is asserted</h2>
 * Absence, not filtered output. Reading tenant A's data back as tenant A proves
 * nothing; the question is only ever whether tenant B can see it. Each report is
 * run twice over the same seeded database — once per tenant — and the rows are
 * checked for markers belonging to the other tenant.
 *
 * <p>{@link CoverageIsExhaustive} then asserts that every report in the service
 * catalogue appears in this file's table. Without it, an eleventh report added
 * next year escapes the isolation suite silently, which is precisely the failure
 * mode this test exists to prevent.
 *
 * <p>Requires Docker; skipped automatically when unavailable, matching
 * {@code AgentGatewayIsolationIntegrationTest}.
 */
@SpringBootTest
@Testcontainers
@DisabledIf("dockerNotAvailable")
@DisplayName("Insurance reports — cross-tenant isolation")
class InsuranceReportTenantIsolationTest {

    static boolean dockerNotAvailable() {
        try {
            return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return true;
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hms_db")
            .withUsername("hms_user")
            .withPassword("hms_pass");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired InsuranceReportDataService data;
    @Autowired InsuranceReportService reportService;
    @Autowired JdbcTemplate jdbc;

    /** Distinctive per-tenant markers, so a leaked row is unmistakable in the assertion message. */
    private static final String A_INSURER = "ISOLATION-TENANT-A-INSURER";
    private static final String B_INSURER = "ISOLATION-TENANT-B-INSURER";
    private static final String A_CHARGE  = "ISOLATION-TENANT-A-CHARGE";
    private static final String B_CHARGE  = "ISOLATION-TENANT-B-CHARGE";

    private static final String FROM = "2020-01-01";
    private static final String TO   = "2099-12-31";
    private static final String AS_ON = "2099-12-31";

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void seed() {
        TenantContext.clear();
        BranchContext.clear();

        tenantA = createTenant("isolation-a", "Isolation Hospital A");
        tenantB = createTenant("isolation-b", "Isolation Hospital B");

        seedClaim(tenantA, A_INSURER, A_CHARGE);
        seedClaim(tenantB, B_INSURER, B_CHARGE);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        BranchContext.clear();
    }

    // ── The coverage table ──────────────────────────────────────────────────

    /**
     * Every report, paired with the way it is invoked. Keeping them in one map
     * is what lets {@link CoverageIsExhaustive} compare this list against the
     * service catalogue.
     */
    private Map<String, Supplier<List<Map<String, Object>>>> allReports() {
        Map<String, Supplier<List<Map<String, Object>>>> m = new LinkedHashMap<>();
        m.put("preauth_raised",              () -> data.getPreAuthRaised(FROM, TO, "ALL"));
        m.put("preauth_status",              () -> data.getPreAuthStatus(FROM, TO, "ALL"));
        m.put("enhancement_raised",          () -> data.getEnhancementRaised(FROM, TO, "ALL"));
        m.put("enhancement_status",          () -> data.getEnhancementStatus(FROM, TO, "ALL"));
        m.put("claim_dispatch",              () -> data.getClaimDispatch(FROM, TO, "ALL"));
        m.put("disallowance_summary",        () -> data.getDisallowanceSummary(FROM, TO, "ALL"));
        m.put("disallowance_detail",         () -> data.getDisallowanceDetail(FROM, TO, "ALL"));
        m.put("document_pending_status",     () -> data.getDocumentPendingStatus(FROM, TO, "ALL"));
        m.put("ip_outstanding_credit_bills", () -> data.getOutstandingCreditBills(AS_ON, "ALL"));
        m.put("insurance_ageing_analysis",   () -> data.getAgeingAnalysis(AS_ON, "ALL"));
        return m;
    }

    // ── Isolation ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("No report returns another tenant's rows")
    class Isolation {

        @Test
        void everyReportExcludesTheOtherTenantsData() {
            List<String> leaks = new ArrayList<>();

            for (var entry : allReports().entrySet()) {
                String report = entry.getKey();

                // Tenant B must not see tenant A.
                TenantContext.set(tenantB);
                List<Map<String, Object>> asB = entry.getValue().get();
                TenantContext.clear();
                if (containsMarker(asB, A_INSURER) || containsMarker(asB, A_CHARGE)) {
                    leaks.add(report + " leaked tenant A's rows to tenant B");
                }

                // And symmetrically, in case a predicate is pinned to a literal.
                TenantContext.set(tenantA);
                List<Map<String, Object>> asA = entry.getValue().get();
                TenantContext.clear();
                if (containsMarker(asA, B_INSURER) || containsMarker(asA, B_CHARGE)) {
                    leaks.add(report + " leaked tenant B's rows to tenant A");
                }
            }

            // One assertion listing every offender, rather than failing on the
            // first: if the predicate was omitted from one query it was probably
            // omitted from several, and fixing them one build at a time is slow.
            assertThat(leaks)
                .as("raw-JDBC reports bypass the Hibernate tenant filter; each query "
                    + "must append scope.predicate(\"i\") AND scope.args()")
                .isEmpty();
        }

        @Test
        void eachTenantStillSeesItsOwnData() {
            // The companion to the test above. Without this, a query that
            // returned nothing at all would pass isolation while being useless,
            // and the suite would be green on a broken report.
            TenantContext.set(tenantA);
            try {
                assertThat(data.getPreAuthRaised(FROM, TO, "ALL"))
                    .as("tenant A should see its own pre-auth request")
                    .anyMatch(r -> A_INSURER.equals(r.get("insurer")));

                assertThat(data.getDisallowanceDetail(FROM, TO, "ALL"))
                    .as("tenant A should see its own disallowed charge")
                    .anyMatch(r -> A_CHARGE.equals(r.get("charge")));

                assertThat(data.getClaimDispatch(FROM, TO, "ALL"))
                    .as("tenant A should see its own dispatched docket")
                    .anyMatch(r -> A_INSURER.equals(r.get("insurer")));
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        void aBranchPinnedUserSeesOnlyTheirBranch() {
            // ReportScope adds a branch predicate whenever BranchContext is set.
            // A second branch in the same tenant is the case where a tenant-only
            // predicate looks correct and is still wrong.
            UUID branchTwo = createBranch(tenantA, "ISO-BR-2");
            String otherBranchInsurer = "ISOLATION-A-BRANCH-2";
            seedClaim(tenantA, branchTwo, otherBranchInsurer, "ISO-A-BR2-CHARGE");

            TenantContext.set(tenantA);
            BranchContext.set(defaultBranch(tenantA));
            try {
                assertThat(data.getPreAuthRaised(FROM, TO, "ALL"))
                    .as("a branch-pinned user must not see a sibling branch's claims")
                    .noneMatch(r -> otherBranchInsurer.equals(r.get("insurer")));
            } finally {
                BranchContext.clear();
                TenantContext.clear();
            }
        }

        @Test
        void anUnscopedContextIsNotSilentlyTreatedAsATenant() {
            // With no TenantContext, ReportScope emits no predicate — the
            // platform-wide SUPERADMIN view. Asserting it explicitly means a
            // future change that starts defaulting to some tenant shows up here
            // rather than as mysteriously empty reports.
            TenantContext.clear();
            BranchContext.clear();

            List<Map<String, Object>> all = data.getPreAuthRaised(FROM, TO, "ALL");
            assertThat(all).anyMatch(r -> A_INSURER.equals(r.get("insurer")));
            assertThat(all).anyMatch(r -> B_INSURER.equals(r.get("insurer")));
        }
    }

    // ── Coverage ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Coverage")
    class CoverageIsExhaustive {

        @Test
        void everyReportInTheCatalogueIsExercisedByThisTest() {
            List<String> catalogue = reportService.getAvailableReports().stream()
                .map(r -> r.get("name"))
                .toList();

            assertThat(allReports().keySet())
                .as("a report that is not in this test's table never gets an isolation "
                    + "check — add it here at the same time as the query")
                .containsExactlyInAnyOrderElementsOf(catalogue);
        }
    }

    // ── Empty-result behaviour ──────────────────────────────────────────────

    @Nested
    @DisplayName("Empty results")
    class EmptyResults {

        @Test
        void everyReportReturnsCleanlyForATenantWithNoClaims() {
            // A brand-new hospital opens these reports on day one. An exception
            // there reads as a broken product, and the empty path is exactly the
            // one that never gets manual testing.
            UUID emptyTenant = createTenant("isolation-empty", "Isolation Hospital Empty");
            TenantContext.set(emptyTenant);
            try {
                for (var entry : allReports().entrySet()) {
                    List<Map<String, Object>> rows = entry.getValue().get();
                    assertThat(rows).as("%s should return result, not throw", entry.getKey()).isNotNull();
                    boolean isEmpty = rows.isEmpty() || (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__")));
                    assertThat(isEmpty).as("%s should return empty result, not throw", entry.getKey()).isTrue();
                }
            } finally {
                TenantContext.clear();
            }
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────

    private boolean containsMarker(List<Map<String, Object>> rows, String marker) {
        return rows.stream()
            .anyMatch(row -> row.values().stream()
                .anyMatch(v -> v != null && marker.equals(v.toString())));
    }

    private UUID createTenant(String slug, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, slug, name, status) VALUES (?, ?, ?, 1)",
            id, slug + "-" + id, name);
        jdbc.update("INSERT INTO branches (id, tenant_id, code, name, is_default, status) "
                  + "VALUES (?, ?, ?, ?, TRUE, 1)",
            UUID.randomUUID(), id, "MAIN-" + id, name + " Main");
        return id;
    }

    private UUID createBranch(UUID tenantId, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO branches (id, tenant_id, code, name, is_default, status) "
                  + "VALUES (?, ?, ?, ?, FALSE, 1)",
            id, tenantId, code + "-" + id, code);
        return id;
    }

    private UUID defaultBranch(UUID tenantId) {
        return jdbc.queryForObject(
            "SELECT id FROM branches WHERE tenant_id = ? AND is_default LIMIT 1",
            UUID.class, tenantId);
    }

    private void seedClaim(UUID tenantId, String insurerName, String chargeName) {
        seedClaim(tenantId, defaultBranch(tenantId), insurerName, chargeName);
    }

    /**
     * One claim carried all the way through the desk flow, so that every one of
     * the ten reports has something to return. A claim seeded only up to stage 1
     * would leave the dispatch and settlement reports empty, and an empty report
     * cannot leak — the isolation test would pass without testing anything.
     */
    private void seedClaim(UUID tenantId, UUID branchId, String insurerName, String chargeName) {
        UUID patientId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO patients (id, tenant_id, branch_id, first_name, last_name, gender,
                                  estimated_date_of_birth, is_clinical_trial, status)
            VALUES (?, ?, ?, 'Isolation', 'Patient', 0, DATE '1980-01-01', FALSE, 1)
            """, patientId, tenantId, branchId);

        UUID billId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO bills (id, tenant_id, branch_id, patient_id, bill_amount, discount_total,
                               payment_total, bill_status, status, bill_type, encounter_type,
                               bill_date, bill_number)
            VALUES (?, ?, ?, ?, 10000000, 0, 0, 1, 1, 0, 0, DATE '2024-06-01', ?)
            """, billId, tenantId, branchId, patientId, "B-" + billId);

        // charge_line_items.service_catalog_item_id carries an FK to
        // service_catalog_items, which in turn requires a service_categories
        // row. A random UUID here fails on insert, not at assertion time, so
        // the whole chain has to be seeded.
        UUID categoryId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO service_categories (id, tenant_id, branch_id, name, category_type, status)
            VALUES (?, ?, ?, ?, 0, 1)
            """, categoryId, tenantId, branchId, "Isolation Category " + categoryId);

        UUID catalogItemId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO service_catalog_items (id, tenant_id, branch_id, name, category_id,
                                               service_type, requires_order, status)
            VALUES (?, ?, ?, ?, ?, 0, FALSE, 1)
            """, catalogItemId, tenantId, branchId, chargeName, categoryId);

        // A disallowed line, so disallowance_detail has a row to return.
        jdbc.update("""
            INSERT INTO charge_line_items (id, tenant_id, branch_id, bill_id,
                                           service_catalog_item_id, item_name, amount, unit_rate,
                                           quantity, discount_amount, disallowed_amount, status)
            VALUES (?, ?, ?, ?, ?, ?, 5000000, 5000000, 1, 0, 250000, 1)
            """, UUID.randomUUID(), tenantId, branchId, billId, catalogItemId, chargeName);

        UUID insuranceId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO insurances (
                id, tenant_id, branch_id, patient_id, bill_id, insurer_name, tpa_name,
                insurance_status, status,
                preauth_applied_date, preauth_requested_amount, preauth_communication_to_tpa,
                preauth_created_date,
                claim_no, preauth_approval_status, preauth_date_of_approval, preauth_approved_limit,
                preauth_approval_created_date,
                enhancement_applied_date, enhancement_requested_amount,
                enhancement_communication_to_tpa, enhancement_created_date,
                enhancement_approval_status, enhancement_date_of_approval,
                enhancement_approved_limit, enhancement_approval_created_date,
                checklist, check_list_created_date,
                mode_of_dispatch, courier, pod_no, dispatch_date, dispatched_by,
                dispatch_created_date,
                disallowance_created_date, insurance_current_status, created_at)
            VALUES (
                ?, ?, ?, ?, ?, ?, 'Isolation TPA',
                'SETTLED', 1,
                TIMESTAMPTZ '2024-06-02 10:00:00+00', 9000000, 'FAX',
                TIMESTAMPTZ '2024-06-02 10:00:00+00',
                ?, 'APPROVED', TIMESTAMPTZ '2024-06-03 10:00:00+00', 8000000,
                TIMESTAMPTZ '2024-06-03 10:00:00+00',
                TIMESTAMPTZ '2024-06-05 10:00:00+00', 11000000,
                'FAX', TIMESTAMPTZ '2024-06-05 10:00:00+00',
                'APPROVED', TIMESTAMPTZ '2024-06-06 10:00:00+00',
                10000000, TIMESTAMPTZ '2024-06-06 10:00:00+00',
                ?::jsonb, TIMESTAMPTZ '2024-06-07 10:00:00+00',
                'COURIER', 'DTDC', ?, TIMESTAMPTZ '2024-06-08 10:00:00+00', 'Isolation Clerk',
                TIMESTAMPTZ '2024-06-08 10:00:00+00',
                TIMESTAMPTZ '2024-06-20 10:00:00+00', 'DISALLOWANCE_ENTRY',
                TIMESTAMPTZ '2024-06-01 10:00:00+00')
            """,
            insuranceId, tenantId, branchId, patientId, billId, insurerName,
            // claim_no is an encrypted column; the converter is bypassed by raw
            // JDBC, which is fine — these reports are only ever asserted on the
            // insurer and charge markers, never on the claim number.
            "CLAIM-" + insuranceId,
            "{\"checklists\":[{\"name\":\"" + chargeName
                + " Doc\",\"toBeSubmit\":2,\"submitted\":1,\"nonSubmission\":\"pending\"}]}",
            "POD-" + insuranceId);

        // A partial payment, so the outstanding and ageing reports both have a
        // non-zero balance to report and cannot pass by being empty.
        jdbc.update("""
            INSERT INTO insurance_cheque_receipts (id, tenant_id, branch_id, insurance_id,
                                                   cheque_no, cheque_date, drawn_on, amount, status)
            VALUES (?, ?, ?, ?, ?, DATE '2024-06-20', 'Isolation Bank', 6000000, 1)
            """, UUID.randomUUID(), tenantId, branchId, insuranceId, "CHQ-" + insuranceId);

        // A second claim left mid-flow, so document_pending_status has a row.
        UUID pendingId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO insurances (
                id, tenant_id, branch_id, patient_id, insurer_name, insurance_status, status,
                preauth_applied_date, preauth_requested_amount, preauth_created_date,
                preauth_approval_status, preauth_date_of_approval, preauth_approved_limit,
                insurance_current_status, created_at)
            VALUES (?, ?, ?, ?, ?, 'PRE_AUTH_RECEIVED', 1,
                    TIMESTAMPTZ '2024-06-02 10:00:00+00', 4000000,
                    TIMESTAMPTZ '2024-06-02 10:00:00+00',
                    'APPROVED', TIMESTAMPTZ '2024-06-03 10:00:00+00', 4000000,
                    'PREAUTHORISATION_APPROVAL', TIMESTAMPTZ '2024-06-01 10:00:00+00')
            """, pendingId, tenantId, branchId, patientId, insurerName);
    }

    /** Guards the fixture itself: a silently unseeded table makes every report empty. */
    @Test
    void fixtureSeedsBothTenants() {
        assertThat(countInsurances(tenantA)).isEqualTo(2);
        assertThat(countInsurances(tenantB)).isEqualTo(2);
        assertThat(LocalDate.parse(AS_ON)).isAfter(LocalDate.parse("2024-06-01"));
    }

    private int countInsurances(UUID tenantId) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM insurances WHERE tenant_id = ?", Integer.class, tenantId);
        return n == null ? 0 : n;
    }
}
