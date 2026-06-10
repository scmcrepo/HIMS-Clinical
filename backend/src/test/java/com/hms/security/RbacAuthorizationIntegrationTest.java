package com.hms.security;

import com.hms.application.role.RoleManagementService;
import com.hms.api.role.response.RoleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end RBAC verification.
 *
 * <p>Boots the full Spring context against a real PostgreSQL 16 container so that
 * Flyway applies every migration (including V088 / V089 RBAC seeds). This alone
 * catches the most common regression: a broken migration or a feature key used in
 * a guard but never seeded.
 *
 * <p>Then it drives the actual security pipeline — {@code @PreAuthorize} +
 * {@link HmsPermissionEvaluator} — through MockMvc, asserting the allow/deny matrix
 * for representative endpoints, and finally checks that the seeded default role
 * grants (V088 + V089) match expectations.
 *
 * <p>Requires Docker to be running on the host. No other setup needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("RBAC authorization — backend enforcement + seed verification")
class RbacAuthorizationIntegrationTest {

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
        // Flyway owns the schema; Hibernate must not touch it.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired MockMvc mvc;
    @Autowired RoleManagementService roleService;

    // ── Test principals (built directly; the evaluator reads authorities + principal) ──

    /** SUPERADMIN: no feature keys, but the role name triggers the evaluator bypass. */
    private UserDetails superadmin() {
        return new HmsUserDetails(UUID.randomUUID(), "superadmin", "x", false,
            Set.of(), Set.of("SUPERADMIN"), null, null);
    }

    private UserDetails userWith(String role, Set<String> featureKeys) {
        return new HmsUserDetails(UUID.randomUUID(), role.toLowerCase(), "x", false,
            featureKeys, Set.of(role), null, null);
    }

    // Representative guarded GET endpoints (parameter-free, return a list on success):
    //   GET /roles/features  -> requires SETTINGS_ROLE
    //   GET /areas           -> requires SETTINGS_AREA  (class-level guard)
    //   GET /roles           -> any authenticated user (no feature guard)
    private static final String SETTINGS_ROLE_ENDPOINT = "/roles/features";
    private static final String SETTINGS_AREA_ENDPOINT = "/areas";
    private static final String AUTH_ONLY_ENDPOINT     = "/roles";

    @Nested
    @DisplayName("Enforcement matrix")
    class Enforcement {

        @Test
        @DisplayName("unauthenticated request is rejected with 401")
        void unauthenticatedIsUnauthorized() throws Exception {
            mvc.perform(get(SETTINGS_ROLE_ENDPOINT))
               .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("SUPERADMIN bypasses every feature check")
        void superadminBypassesEverything() throws Exception {
            mvc.perform(get(SETTINGS_ROLE_ENDPOINT).with(user(superadmin())))
               .andExpect(status().isOk());
            mvc.perform(get(SETTINGS_AREA_ENDPOINT).with(user(superadmin())))
               .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a user is allowed only on endpoints whose feature it holds")
        void featureScopedAllowAndDeny() throws Exception {
            UserDetails roleAdmin = userWith("CONFIG_ADMIN", Set.of("SETTINGS_ROLE"));
            UserDetails areaAdmin = userWith("AREA_ADMIN", Set.of("SETTINGS_AREA"));

            // Holder of SETTINGS_ROLE: allowed on /roles/features, denied on /areas.
            mvc.perform(get(SETTINGS_ROLE_ENDPOINT).with(user(roleAdmin)))
               .andExpect(status().isOk());
            mvc.perform(get(SETTINGS_AREA_ENDPOINT).with(user(roleAdmin)))
               .andExpect(status().isForbidden());

            // Holder of SETTINGS_AREA: the mirror image.
            mvc.perform(get(SETTINGS_AREA_ENDPOINT).with(user(areaAdmin)))
               .andExpect(status().isOk());
            mvc.perform(get(SETTINGS_ROLE_ENDPOINT).with(user(areaAdmin)))
               .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("a user with no features is denied on guarded endpoints but reaches auth-only ones")
        void noFeaturesStillAuthenticated() throws Exception {
            UserDetails plain = userWith("USER", Set.of());
            mvc.perform(get(SETTINGS_ROLE_ENDPOINT).with(user(plain)))
               .andExpect(status().isForbidden());
            mvc.perform(get(AUTH_ONLY_ENDPOINT).with(user(plain)))
               .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Seed verification (V088 + V089)")
    class SeedData {

        @Test
        @DisplayName("standard operational roles are seeded")
        void rolesSeeded() {
            List<String> names = roleService.getAll().stream().map(RoleResponse::name).toList();
            assertThat(names).contains(
                "ADMIN", "DOCTOR", "NURSE", "LAB", "RADIOLOGY",
                "RECEPTION", "BILLING", "PHARMACY", "STOCK");
        }

        @Test
        @DisplayName("ADMIN holds every seeded feature")
        void adminHasAllFeatures() {
            RoleResponse admin = role("ADMIN");
            long totalFeatures = roleService.getAllFeatures().size();
            assertThat(admin.features()).hasSize((int) totalFeatures);
        }

        @Test
        @DisplayName("default grants give each role its expected features")
        void defaultGrantsApplied() {
            assertThat(keysOf("DOCTOR")).contains("OUT_PATIENT", "IN_PATIENT", "LAB_REPORT");
            assertThat(keysOf("RECEPTION")).contains("REGISTRATION", "APPOINTMENT");
            assertThat(keysOf("BILLING")).contains("PATIENT_BILLS", "PAYMENT");
            assertThat(keysOf("LAB")).contains("LAB_REPORT");
            assertThat(keysOf("RADIOLOGY")).contains("RADIOLOGY");
            // LAB must NOT have been granted billing access by mistake.
            assertThat(keysOf("LAB")).doesNotContain("PATIENT_BILLS");
        }

        private RoleResponse role(String name) {
            return roleService.getAll().stream()
                .filter(r -> r.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Role not seeded: " + name));
        }

        private Set<String> keysOf(String roleName) {
            return role(roleName).features().stream()
                .map(RoleResponse.FeatureSummary::featureKey)
                .collect(java.util.stream.Collectors.toSet());
        }
    }
}
