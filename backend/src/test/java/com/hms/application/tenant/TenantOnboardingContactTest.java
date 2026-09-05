package com.hms.application.tenant;

import com.hms.application.grievance.GrievanceService;
import com.hms.infrastructure.persistence.grievance.ComplianceContactJpaRepository;
import com.hms.infrastructure.persistence.grievance.GrievanceEventJpaRepository;
import com.hms.infrastructure.persistence.grievance.GrievanceJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.data.domain.AuditorAware;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.security.FeaturePermissionCacheService;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import com.hms.infrastructure.persistence.shared.FeatureJpaRepository;
import com.hms.infrastructure.persistence.role.RoleJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * WO-032 / F2 — a hospital cannot be onboarded without a publishable grievance
 * contact.
 *
 * <h2>Why this is a test and not a form validator</h2>
 * DPDP s. 8(9) requires every Data Fiduciary to publish a contact point for data
 * principals. All four tenants in the running deployment were onboarded without
 * one. {@code ComplianceContactCoverageCheck} has been logging that at ERROR on
 * every boot, and it stayed unmet anyway — which is the evidence that a field
 * someone can fill in later is a field nobody fills in.
 *
 * <p>The rule therefore lives in {@code TenantService}, so it holds for every
 * caller rather than only for the one screen, and this test pins it there.
 *
 * <h2>What is asserted, and what deliberately is not</h2>
 * Rejection, and that rejection happens <em>before</em> anything is written. A
 * validation that ran after {@code create()} would leave a hospital row, a
 * branch, and a seeded RBAC tree behind on failure — a half-onboarded tenant
 * that is worse than either outcome, because it is live and cannot receive a
 * complaint.
 *
 * <p>The happy path is not asserted here: it runs {@code seedRbac}, which
 * touches four repositories and the EntityManager, and mocking that to green
 * would test the mocks. It is covered by
 * {@code TenantProvisioningAgentFeaturesTest} against a real database.
 */
@DisplayName("Tenant onboarding — s. 8(9) grievance contact is mandatory")
class TenantOnboardingContactTest {

    private TenantJpaRepository tenantRepo;
    private GrievanceService grievanceService;
    private TenantService service;

    @BeforeEach
    void setUp() {
        tenantRepo = mock(TenantJpaRepository.class);
        grievanceService = mock(GrievanceService.class);
        service = new TenantService(
            tenantRepo,
            mock(BranchJpaRepository.class),
            mock(RoleJpaRepository.class),
            mock(FeatureJpaRepository.class),
            mock(FeaturePermissionCacheService.class),
            mock(UserJpaRepository.class),
            mock(PasswordEncoder.class),
            mock(EntityManager.class),
            grievanceService,
            mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    private void onboardWith(String contactName, String contactEmail) {
        service.onboard("apollo", "Apollo Hospital", null, "Chennai", "044-1234",
                        "apollo.admin", "s3cret", "Admin", "User",
                        contactName, "Medical Superintendent", contactEmail, null);
    }

    @Test
    @DisplayName("no contact name is refused, and nothing is written")
    void missingNameIsRefused() {
        assertThatThrownBy(() -> onboardWith("  ", "privacy@apollo.in"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("8(9)");

        verify(tenantRepo, never()).save(any());
        verifyNoInteractions(grievanceService);
    }

    @Test
    @DisplayName("no contact email is refused, and nothing is written")
    void missingEmailIsRefused() {
        assertThatThrownBy(() -> onboardWith("Dr. Priya Raman", null))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("8(9)");

        verify(tenantRepo, never()).save(any());
        verifyNoInteractions(grievanceService);
    }

    @Test
    @DisplayName("an unusable email is refused — this address is published to patients")
    void malformedEmailIsRefused() {
        // Not an RFC 5322 implementation and not trying to be. These are the
        // shapes someone types to get past a required field.
        for (String bad : new String[]{"not-an-email", "@apollo.in", "admin@", "admin@in."}) {
            assertThatThrownBy(() -> onboardWith("Dr. Priya Raman", bad))
                .as("should refuse '%s'", bad)
                .isInstanceOf(BusinessRuleViolationException.class);
        }

        verify(tenantRepo, never()).save(any());
        verifyNoInteractions(grievanceService);
    }

    @Test
    @DisplayName("a contact is never published against a null tenant")
    @SuppressWarnings("unchecked")
    void publishRequiresATenant() {
        // The onboarding path is the only caller with no TenantContext, which is
        // why publishContactForTenant takes the tenant explicitly. Without this
        // guard a null would be stamped as a null tenant_id and the contact would
        // belong to nobody — present in the table, invisible to the coverage
        // check, and unreachable from the public endpoint.
        GrievanceService grievances = new GrievanceService(
            mock(GrievanceJpaRepository.class),
            mock(GrievanceEventJpaRepository.class),
            mock(ComplianceContactJpaRepository.class),
            mock(AuditorAware.class),
            new SimpleMeterRegistry());

        assertThatThrownBy(() -> grievances.publishContactForTenant(
                null, "Dr. Priya Raman", "Medical Superintendent",
                "privacy@apollo.in", null, null, false, true))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("named tenant");
    }
}
