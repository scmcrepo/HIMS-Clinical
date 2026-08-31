package com.hms.application.retention;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.retention.RetentionPolicyEntity;
import com.hms.infrastructure.persistence.retention.RetentionPolicyJpaRepository;
import com.hms.infrastructure.persistence.retention.RetentionRunItemJpaRepository;
import com.hms.infrastructure.persistence.retention.RetentionRunJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Retention — WO-025.
 *
 * <p>This is the only code in the repository that destroys patient records on a
 * schedule, unattended. Almost every test here asserts that something does
 * <em>not</em> happen, because the failure that matters is deletion occurring
 * when it should not have — and unlike every other defect found in this project,
 * that one is not recoverable.
 */
class RetentionServiceTest {

    private RetentionPolicyJpaRepository policies;
    private MeterRegistry meters;
    private RetentionService service;

    /** Mirrors the real tables the seeded policies target. */
    private static final Map<String, Set<String>> SCHEMA = Map.of(
        "portal_sessions", Set.of("id", "tenant_id", "expires_at", "patient_id"),
        "agent_tool_invocations", Set.of("id", "tenant_id", "created_at", "target_entity_id"),
        "hitl_escalations", Set.of("id", "tenant_id", "resolved_at", "patient_id"),
        "appointments", Set.of("id", "appointment_date", "patient_id"),   // no tenant_id
        "clinical_encounters", Set.of("id", "tenant_id", "created_at", "patient_id"));

    @BeforeEach
    void setUp() {
        policies = mock(RetentionPolicyJpaRepository.class);
        meters = new SimpleMeterRegistry();
        service = new RetentionService(
            policies, mock(RetentionRunJpaRepository.class),
            mock(RetentionRunItemJpaRepository.class), meters);
        ReflectionTestUtils.setField(service, "schemaSnapshot", SCHEMA);
        when(policies.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private RetentionPolicyEntity policy(String store, String dateCol, String action) {
        RetentionPolicyEntity p = new RetentionPolicyEntity();
        p.setTargetStore(store);
        p.setDateColumn(dateCol);
        p.setAction(action);
        p.setRetentionDays(30);
        p.setJustification("because");
        p.setEnabled(false);
        p.setDryRun(true);
        p.setMaxRowsPerRun(500);
        return p;
    }

    // ── Defaults are the safe ones ────────────────────────────────────────

    @Test
    @DisplayName("A new policy is disabled and in dry-run")
    void defaultsAreSafe() {
        RetentionPolicyEntity p = new RetentionPolicyEntity();
        assertThat(p.isEnabled()).isFalse();
        assertThat(p.isDryRun()).isTrue();
        assertThat(p.isLive()).isFalse();
    }

    @Test
    @DisplayName("Every policy seeded by V213 is disabled and in dry-run")
    void seededPoliciesAreInert() throws IOException {
        String sql = Files.readString(Paths.get(
            "src/main/resources/db/migration/V213__retention_policy_engine.sql"));

        // The periods are engineering defaults that no lawyer has reviewed. A
        // hospital discovering its retention policy by watching records vanish
        // is worse than one that never turns the job on.
        assertThat(sql).contains("FALSE, TRUE, 500");
        assertThat(sql).doesNotContain("TRUE, FALSE,");
    }

    // ── The never-sweep list ──────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"clinical_encounters", "patients", "consent_records",
                            "erasure_requests", "security_incidents", "grievances",
                            "retention_policies", "users"})
    @DisplayName("Protected stores are refused even if a policy row exists for them")
    void neverSweepStoresAreRefused(String store) {
        // Someone could insert a policy through the API or straight into the
        // database. This is the backstop that stops it destroying clinical
        // evidence or the audit trail proving the system behaved lawfully.
        RetentionPolicyEntity p = policy(store, "created_at", "DELETE");
        assertThat(service.validate(p)).contains("never-sweep");
    }

    // ── Schema validation ─────────────────────────────────────────────────

    @Test
    @DisplayName("A policy naming a column that does not exist is rejected")
    void unknownColumnRejected() {
        assertThat(service.validate(policy("portal_sessions", "nope_at", "DELETE")))
            .contains("does not exist");
    }

    @Test
    @DisplayName("A policy naming a table that does not exist is rejected")
    void unknownTableRejected() {
        assertThat(service.validate(policy("not_a_table", "created_at", "DELETE")))
            .contains("table does not exist");
    }

    @Test
    @DisplayName("A table with no tenant_id is rejected — the sweep cannot be scoped")
    void tableWithoutTenantIdRejected() {
        // The job runs with no tenant context, so the Hibernate filter is off.
        // The tenant predicate in the statement is the only thing keeping one
        // hospital's policy from reaching another's rows.
        assertThat(service.validate(policy("appointments", "appointment_date", "ANONYMISE")))
            .contains("no tenant_id");
    }

    @Test
    @DisplayName("ANONYMISE against a column the table lacks is rejected")
    void anonymiseColumnMustExist() {
        RetentionPolicyEntity p = policy("agent_tool_invocations", "created_at", "ANONYMISE");
        p.setAnonymiseColumn("patient_id");   // this table has target_entity_id instead

        assertThat(service.validate(p)).contains("anonymise_column");
    }

    @Test
    @DisplayName("ANONYMISE against the right column passes")
    void anonymiseColumnResolves() {
        RetentionPolicyEntity p = policy("agent_tool_invocations", "created_at", "ANONYMISE");
        p.setAnonymiseColumn("target_entity_id");

        assertThat(service.validate(p)).isNull();
    }

    // ── Identifier safety ─────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "portal_sessions; DROP TABLE patients",
        "portal_sessions WHERE 1=1",
        "Portal_Sessions",
        "portal sessions",
        "'portal_sessions'"})
    @DisplayName("Anything that is not a plain lowercase identifier is refused")
    void unsafeIdentifiersRefused(String store) {
        // Table and column names come from a table an administrator can edit, so
        // they are the one untrusted input that reaches SQL construction.
        assertThat(service.validate(policy(store, "expires_at", "DELETE"))).isNotNull();
    }

    @Test
    @DisplayName("A zero or negative retention period is refused")
    void nonPositiveRetentionRefused() {
        RetentionPolicyEntity p = policy("portal_sessions", "expires_at", "DELETE");
        p.setRetentionDays(0);
        // Zero would delete rows the moment they were written.
        assertThat(service.validate(p)).contains("positive");
    }

    // ── Arming a policy ───────────────────────────────────────────────────

    @Test
    @DisplayName("A policy that does not validate cannot be taken out of dry-run")
    void cannotArmAnInvalidPolicy() {
        RetentionPolicyEntity broken = policy("portal_sessions", "nope_at", "DELETE");
        UUID id = UUID.randomUUID();
        when(policies.findById(id)).thenReturn(java.util.Optional.of(broken));

        // Enabling a policy with a misnamed column is how a typo becomes data
        // loss — either it silently matches nothing, or it throws mid-sweep.
        assertThatThrownBy(() ->
            service.update(id, null, true, false, null, null))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("dry-run");

        assertThat(broken.isDryRun()).isTrue();
    }

    @Test
    @DisplayName("A valid policy can be armed, and isLive then reports true")
    void validPolicyCanBeArmed() {
        RetentionPolicyEntity p = policy("portal_sessions", "expires_at", "DELETE");
        UUID id = UUID.randomUUID();
        when(policies.findById(id)).thenReturn(java.util.Optional.of(p));

        service.update(id, null, true, false, null, null);

        assertThat(p.isLive()).isTrue();
    }

    @Test
    @DisplayName("The batch cap is clamped rather than trusted")
    void batchCapIsClamped() {
        RetentionPolicyEntity p = policy("portal_sessions", "expires_at", "DELETE");
        UUID id = UUID.randomUUID();
        when(policies.findById(id)).thenReturn(java.util.Optional.of(p));

        service.update(id, null, null, null, 999_999, null);
        assertThat(p.getMaxRowsPerRun()).isEqualTo(10_000);

        service.update(id, null, null, null, 0, null);
        assertThat(p.getMaxRowsPerRun()).isEqualTo(1);
    }

    @Test
    @DisplayName("A negative retention period is refused on update, not just at seed time")
    void updateRejectsNonPositiveDays() {
        RetentionPolicyEntity p = policy("portal_sessions", "expires_at", "DELETE");
        UUID id = UUID.randomUUID();
        when(policies.findById(id)).thenReturn(java.util.Optional.of(p));

        assertThatThrownBy(() -> service.update(id, -5, null, null, null, null))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── No live manual trigger ────────────────────────────────────────────

    @Test
    @DisplayName("The controller exposes no endpoint that runs policies live on demand")
    void noLiveManualTrigger() throws IOException {
        String controller = Files.readString(Paths.get(
            "src/main/java/com/hms/api/retention/RetentionController.java"));

        // Destruction happens on a schedule, after someone read a preview and
        // armed the policy — not because a button was available on a bad
        // afternoon. The only manual trigger forces dry-run.
        assertThat(controller).contains("service.execute(Boolean.TRUE)");
        assertThat(controller).doesNotContain("service.execute(null)");
        assertThat(controller).doesNotContain("service.execute(Boolean.FALSE)");
    }
}
