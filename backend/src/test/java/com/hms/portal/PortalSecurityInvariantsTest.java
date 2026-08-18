package com.hms.portal;

import com.hms.security.HmsUserDetails;
import com.hms.security.portal.PortalPrincipalFactory;
import com.hms.security.portal.PortalTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants that are invisible at runtime in a working system.
 *
 * <p>Each of these has a failure mode that produces no error, no exception and
 * no failing request — just a patient quietly seeing more than they should. That
 * is what makes them worth asserting rather than reviewing.
 */
class PortalSecurityInvariantsTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();

    @Test
    @DisplayName("a portal patient is never treated as a hospital admin")
    void patientIsNeverHospitalAdmin() {
        HmsUserDetails principal = PortalPrincipalFactory.patient(PATIENT, TENANT, BRANCH);

        // HmsUserDetails.isHospitalAdmin() is true for any principal with a
        // tenant and no branch. A patient principal that lost its branch would
        // therefore be granted every branch in the hospital, silently, with the
        // tenant filter still passing. This is the reason branchId is required.
        assertThat(principal.isHospitalAdmin()).isFalse();
        assertThat(principal.isSuperAdmin()).isFalse();
        assertThat(principal.getBranchId()).isEqualTo(BRANCH);
        assertThat(principal.getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("a patient principal cannot be built without a branch")
    void patientRequiresBranch() {
        assertThatThrownBy(() -> PortalPrincipalFactory.patient(PATIENT, TENANT, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortalPrincipalFactory.patient(PATIENT, null, BRANCH))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a portal patient carries exactly one feature key")
    void patientCarriesOnlyPortalScope() {
        HmsUserDetails principal = PortalPrincipalFactory.patient(PATIENT, TENANT, BRANCH);

        // Every staff feature is scoped to a tenant rather than to a row, so a
        // patient principal holding one could read every patient in the
        // hospital. The set must stay at exactly one key.
        assertThat(principal.getFeatureKeys())
            .containsExactly(PortalTokenService.SCOPE_PATIENT);
        assertThat(principal.getFeatureKeys())
            .doesNotContain("REGISTRATION", "APPOINTMENT", "MEDICAL_RECORD", "OUT_PATIENT");
    }

    @Test
    @DisplayName("the identity principal is not an HmsUserDetails")
    void identityPrincipalIsNotUserDetails() {
        Object principal = PortalPrincipalFactory.identity("token-abc");

        // TenantResolutionFilter answers an authenticated non-superadmin
        // HmsUserDetails with a null tenant by returning 403 "No tenant
        // assigned". If this ever becomes an HmsUserDetails, every
        // identity-scope endpoint breaks at once.
        assertThat(principal).isNotInstanceOf(HmsUserDetails.class);
    }

    @Test
    @DisplayName("the identity principal never prints its token")
    void identityPrincipalDoesNotLeakTokenInToString() {
        var principal = PortalPrincipalFactory.identity("secret-hmac-token");
        assertThat(principal.toString()).doesNotContain("secret-hmac-token");
    }

    @Test
    @DisplayName("the cross-tenant lookup is the only native query in the portal")
    void onlyOneNativeQueryInPortalPackages() throws Exception {
        Path root = Path.of("src/main/java/com/hms");
        List<Path> portalFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            portalFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("portal"))
                .toList();
        }

        List<String> withNativeQuery = portalFiles.stream()
            .filter(p -> {
                try {
                    return Files.readString(p).contains("nativeQuery = true");
                } catch (Exception e) {
                    return false;
                }
            })
            .map(p -> p.getFileName().toString())
            .toList();

        // One deliberate exception, documented in that file. A second one
        // appearing means somebody has added an unfiltered cross-tenant read
        // without the two-pass enrichment that makes the first one safe.
        assertThat(withNativeQuery).containsExactly("PortalPatientLookupRepository.java");
    }

    @Test
    @DisplayName("the cross-tenant lookup projects ids only")
    void crossTenantLookupReturnsIdsOnly() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/hms/infrastructure/persistence/portal/"
            + "PortalPatientLookupRepository.java"));

        // Widening this SELECT is how a cross-tenant lookup becomes a
        // cross-tenant data leak: these columns are encrypted at rest and would
        // be decrypted outside any tenant scope.
        for (String forbidden : List.of(
                "first_name", "last_name", "contact_number,", "email", "address",
                "date_of_birth", "blood_group")) {
            assertThat(source)
                .as("cross-tenant lookup must not select %s", forbidden)
                .doesNotContain(forbidden);
        }
        assertThat(source).contains("p.status = 1");
    }

    @Test
    @DisplayName("no portal source logs a name, a code or a raw mobile number")
    void portalLogsCarryNoPii() throws Exception {
        Path root = Path.of("src/main/java/com/hms");
        List<Path> portalFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            portalFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("portal"))
                .toList();
        }

        for (Path file : portalFiles) {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("log.")) continue;
                assertThat(trimmed)
                    .as("log line in %s must not interpolate personal data", file.getFileName())
                    .doesNotContain("getFirstName")
                    .doesNotContain("getLastName")
                    .doesNotContain("getContactNumber")
                    .doesNotContain("rawMobile")
                    .doesNotContain("body.mobile")
                    // The code itself must never be logged, not even at DEBUG:
                    // an SMS code in a log file is a working credential.
                    .doesNotContain("code)")
                    .doesNotContain("{code}");
            }
        }
    }
}
