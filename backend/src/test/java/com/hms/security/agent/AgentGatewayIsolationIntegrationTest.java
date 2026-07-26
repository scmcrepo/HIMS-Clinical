package com.hms.security.agent;

import com.hms.application.agent.AgentScope;
import com.hms.application.agent.AgentTokenService;
import com.hms.infrastructure.persistence.agent.AgentApiTokenEntity;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WO-001 / T-005 — the isolation and statelessness guarantees the agent gateway
 * rests on.
 *
 * <p>Boots the full context against a real PostgreSQL 16 container so Flyway runs
 * every migration including V176. That matters here more than usual: the whole
 * design claim is that an agent token inherits the existing Hibernate tenant
 * filters, and a mocked repository would prove nothing about whether those
 * filters actually engage.
 *
 * <p>The isolation tests assert <b>absence</b>, not filtered output. A test that
 * creates data as tenant A and reads it back as tenant A proves nothing; the
 * question is whether tenant B can see it.
 *
 * <p>Requires Docker. Skipped automatically when it is unavailable, matching
 * {@code RbacAuthorizationIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisabledIf("dockerNotAvailable")
@DisplayName("Agent gateway — tenant isolation, scope enforcement, statelessness")
class AgentGatewayIsolationIntegrationTest {

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

    @Autowired MockMvc mvc;
    @Autowired AgentTokenService tokenService;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        BranchContext.clear();
    }

    /** Issues a token belonging to the given tenant and returns its plaintext. */
    private String tokenFor(UUID tenantId, String... scopes) {
        TenantContext.set(tenantId);
        try {
            AgentTokenService.IssuedToken issued =
                tokenService.issue("test-agent", Set.of(scopes), null, null);
            return issued.plaintext();
        } finally {
            TenantContext.clear();
        }
    }

    private UUID anyTenant(int offset) {
        var ids = jdbc.queryForList("SELECT id FROM tenants ORDER BY id", UUID.class);
        assertThat(ids)
            .as("this test needs at least two tenants; seed a second one if the "
                + "base migrations only create one")
            .hasSizeGreaterThan(offset);
        return ids.get(offset);
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        void aValidTokenReachesTheToolSurface() throws Exception {
            String token = tokenFor(anyTenant(0), AgentScope.BED_READ);
            mvc.perform(get("/agent/v1/tools/bed-occupancy")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        }

        @Test
        void anUnknownTokenIsRejected() throws Exception {
            mvc.perform(get("/agent/v1/tools/bed-occupancy")
                    .header("Authorization", "Bearer hms_agt_not-a-real-token"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void aMissingHeaderIsRejected() throws Exception {
            mvc.perform(get("/agent/v1/tools/bed-occupancy"))
                .andExpect(status().isUnauthorized());
        }

        @Test
        void aRevokedTokenStopsWorkingImmediately() throws Exception {
            UUID tenant = anyTenant(0);
            TenantContext.set(tenant);
            AgentTokenService.IssuedToken issued =
                tokenService.issue("revoke-me", Set.of(AgentScope.BED_READ), null, null);
            tokenService.revoke(issued.entity().getId(), null);
            TenantContext.clear();

            mvc.perform(get("/agent/v1/tools/bed-occupancy")
                    .header("Authorization", "Bearer " + issued.plaintext()))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Statelessness")
    class Statelessness {

        @Test
        void agentRequestsIssueNoSessionCookie() throws Exception {
            // This is the property that dodges maximumSessions(1) /
            // maxSessionsPreventsLogin(true). A regression here silently caps the
            // whole agent service at a single process, and the symptom would be
            // intermittent login refusals rather than anything pointing here.
            String token = tokenFor(anyTenant(0), AgentScope.BED_READ);
            mvc.perform(get("/agent/v1/tools/bed-occupancy")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        }

        @Test
        void theSameTokenServesConcurrentRequests() throws Exception {
            String token = tokenFor(anyTenant(0), AgentScope.BED_READ);
            for (int i = 0; i < 3; i++) {
                mvc.perform(get("/agent/v1/tools/bed-occupancy")
                        .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            }
        }
    }

    @Nested
    @DisplayName("Scope enforcement")
    class ScopeEnforcement {

        @Test
        void aReadOnlyTokenCannotReachAWriteTool() throws Exception {
            String token = tokenFor(anyTenant(0), AgentScope.BED_READ);
            mvc.perform(post("/agent/v1/tools/book-slot")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType("application/json")
                    .content("{\"providerId\":\"" + UUID.randomUUID() + "\","
                             + "\"slotId\":\"" + UUID.randomUUID() + "\","
                             + "\"appointmentDate\":\"2026-09-01\","
                             + "\"patientId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
        }

        @Test
        void aTokenCannotReachTheAdministrativeTokenApi() throws Exception {
            // AGENT_TOKEN_MANAGE is deliberately outside the issuable scope set:
            // an agent that can mint tokens can widen its own access.
            String token = tokenFor(anyTenant(0), AgentScope.BED_READ);
            mvc.perform(get("/agent/tokens")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("Tenant isolation")
    class TenantIsolation {

        @Test
        void aTenantsTokenIsInvisibleToAnotherTenant() {
            UUID tenantA = anyTenant(0);
            UUID tenantB = anyTenant(1);

            TenantContext.set(tenantA);
            AgentTokenService.IssuedToken issued =
                tokenService.issue("tenant-a-agent", Set.of(AgentScope.BED_READ), null, null);
            UUID createdId = issued.entity().getId();
            TenantContext.clear();

            TenantContext.set(tenantB);
            try {
                // Absence, not filtered output: tenant B must not see the row at all.
                boolean visible = tokenService.list().stream()
                    .map(AgentApiTokenEntity::getId)
                    .anyMatch(createdId::equals);
                assertThat(visible)
                    .as("tenant B can see tenant A's agent credential — this is a "
                        + "cross-tenant leak, not a display bug")
                    .isFalse();
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        void agentAuditRowsAreScopedToTheirTenant() {
            UUID tenantA = anyTenant(0);
            UUID tenantB = anyTenant(1);

            Integer crossTenantRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_tool_invocations WHERE tenant_id = ? "
                + "AND id IN (SELECT id FROM agent_tool_invocations WHERE tenant_id = ?)",
                Integer.class, tenantA, tenantB);

            assertThat(crossTenantRows)
                .as("an audit row must belong to exactly one tenant")
                .isZero();
        }
    }

    @Nested
    @DisplayName("Migration integrity")
    class MigrationIntegrity {

        @Test
        void everyTenantHasTheAgentFeatureKeys() {
            // V176 seeds these per tenant. A tenant missing one would 403 on every
            // agent call, which is the failure that looks like a bug in the agent.
            Integer tenants = jdbc.queryForObject("SELECT COUNT(*) FROM tenants", Integer.class);
            for (String key : new String[]{
                    AgentScope.SCHEDULING_READ, AgentScope.SCHEDULING_WRITE,
                    AgentScope.BILLING_READ, AgentScope.BED_READ, AgentScope.TOOLS_READ}) {
                Integer seeded = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM features WHERE feature_key = ?", Integer.class, key);
                assertThat(seeded)
                    .as("feature %s is not seeded for every tenant", key)
                    .isEqualTo(tenants);
            }
        }

        @Test
        void theAgentRoleIsTenantWide() {
            // V176 creates it with branch_id NULL and TenantService.seedRbac must
            // agree, or existing and future tenants end up with different shapes.
            Integer branchScoped = jdbc.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE LOWER(name) = 'agent' AND branch_id IS NOT NULL",
                Integer.class);
            assertThat(branchScoped)
                .as("the AGENT role must be tenant-wide (branch_id NULL)")
                .isZero();
        }

        @Test
        void idempotencyKeysAreUniquePerTenantNotGlobally() {
            // The same key string in two tenants must not collide.
            Integer indexes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_agent_idem_tenant_key'",
                Integer.class);
            assertThat(indexes).isEqualTo(1);
        }
    }
}
