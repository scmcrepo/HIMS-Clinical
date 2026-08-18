package com.hms.security.portal;

import com.hms.security.HmsUserDetails;

import java.util.Set;
import java.util.UUID;

/**
 * Turns verified portal claims into an {@link HmsUserDetails}.
 *
 * <p>Same trick as {@code AgentPrincipalFactory}, for the same reason: {@code
 * TenantResolutionFilter} enables the Hibernate {@code tenantFilter} and {@code
 * branchFilter} only when the {@code SecurityContext} holds an authenticated
 * {@code HmsUserDetails}, and {@code HmsPermissionEvaluator} matches feature
 * keys against {@code getAuthorities()}. Constructing a real principal carrying
 * the token's tenant, branch and scope gets tenant isolation, branch scoping and
 * RBAC without touching a single existing controller.
 *
 * <p>Two things here are load-bearing and easy to undo by accident:
 *
 * <ol>
 *   <li><b>{@code branchId} is never null for a patient principal.</b>
 *       {@code HmsUserDetails.isHospitalAdmin()} returns true for any principal
 *       with a tenant and no branch — so a patient principal missing its branch
 *       would be treated as a hospital administrator and see every branch in the
 *       tenant. The session-exchange path requires a branch for this reason, the
 *       column is {@code NOT NULL}, and
 *       {@code PortalPrincipalFactoryTest#patientIsNeverHospitalAdmin} asserts it.</li>
 *   <li><b>The feature set is exactly one key.</b> Not the patient's roles, not
 *       the tenant catalogue — {@code PORTAL_PATIENT} alone, because every staff
 *       feature is scoped to a tenant rather than to a row and would let a
 *       patient read other patients.</li>
 * </ol>
 */
public final class PortalPrincipalFactory {

    private PortalPrincipalFactory() {}

    /** Full patient principal: this patient, this tenant, this branch. */
    public static HmsUserDetails patient(UUID patientId, UUID tenantId, UUID branchId) {
        if (patientId == null || tenantId == null || branchId == null) {
            throw new IllegalArgumentException(
                "portal patient principal requires patientId, tenantId and branchId");
        }
        return new HmsUserDetails(
            patientId,
            "portal:patient:" + patientId,
            null,                                   // no password: token-authenticated
            false,                                  // not locked
            Set.of(PortalTokenService.SCOPE_PATIENT),
            Set.of("PORTAL_PATIENT"),
            Set.of(),                               // roleIds — portal auth resolves on scopes
            null,                                   // consultantId
            null,                                   // departmentId
            tenantId,
            branchId);
    }

    /**
     * Pre-selection principal: the number is verified, the patient is not yet
     * chosen.
     *
     * <p><b>Deliberately not an {@link HmsUserDetails}.</b> The obvious
     * implementation — an {@code HmsUserDetails} with a null tenant — is a trap:
     * {@code TenantResolutionFilter} responds to an authenticated
     * non-superadmin principal with no tenant by returning 403 "No tenant
     * assigned" and ending the request. Every identity-scope call would fail.
     *
     * <p>Using a distinct type instead means the filter's
     * {@code instanceof HmsUserDetails} check skips these requests entirely and
     * no Hibernate filter is enabled — which is correct, because the two things
     * an identity token can do are inherently pre-tenant: list the hospitals on
     * the platform, and start a registration at one of them. The registration
     * path establishes its own tenant scope explicitly via
     * {@code PortalTenantScope}; it does not inherit one.
     */
    public record PortalIdentityPrincipal(String contactNumberToken) {

        public java.util.Collection<org.springframework.security.core.GrantedAuthority> authorities() {
            return java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    PortalTokenService.SCOPE_IDENTITY));
        }

        @Override
        public String toString() {
            // Never let the HMAC token reach a log line through an accidental
            // string interpolation of the principal.
            return "PortalIdentityPrincipal[verified-number]";
        }
    }

    public static PortalIdentityPrincipal identity(String contactNumberToken) {
        if (contactNumberToken == null || contactNumberToken.isBlank()) {
            // Phrased without naming the field: this message reaches logs, and
            // a message that names the value is one careless edit away from
            // interpolating it.
            throw new IllegalArgumentException("identity principal requires a verified number token");
        }
        return new PortalIdentityPrincipal(contactNumberToken);
    }
}
