package com.hms.infrastructure.persistence.portal;

import com.hms.domain.patient.model.Patient;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * The <b>only</b> deliberately tenant-unfiltered query surface in the portal.
 *
 * <p>Read this before changing anything in this file.
 *
 * <p>The portal's first step is "which hospitals hold a record for this mobile
 * number?", and that question spans tenants by definition — a patient may be
 * registered at three of them and cannot be asked which one before they have
 * been shown the list. Every other query in the portal runs under the Hibernate
 * {@code tenantFilter} enabled by {@code TenantResolutionFilter}. This one
 * cannot, because at the moment it runs there is no authenticated principal and
 * therefore no tenant.
 *
 * <p>Two constraints keep that from becoming a hole, and both are load-bearing:
 *
 * <ol>
 *   <li><b>It returns identifiers and nothing else.</b> No name, no age, no
 *       gender, no patient number — no decryption happens here at all. The
 *       display fields are fetched in a second pass, per candidate tenant, with
 *       the tenant filter explicitly set, so the reading of personal data stays
 *       inside normal tenant scope. Widening this projection is how a
 *       cross-tenant lookup quietly becomes a cross-tenant data leak.</li>
 *   <li><b>It is reachable only after OTP verification.</b> The caller is
 *       {@code PortalLookupService}, which is invoked from the verify path.</li>
 * </ol>
 *
 * <p>{@code PortalRepositoryConventionTest} asserts that this remains the only
 * native query in the portal packages.
 */
@Repository
public interface PortalPatientLookupRepository extends org.springframework.data.repository.Repository<Patient, UUID> {

    /**
     * Patient and tenant ids for every active patient carrying this phone token,
     * across all tenants.
     *
     * <p>A native query rather than JPQL on purpose: JPQL against the
     * {@code Patient} entity would have the {@code tenantFilter} applied
     * whenever a session happens to have it enabled, which would make the
     * result depend on how the caller arrived — sometimes cross-tenant,
     * sometimes not. Native SQL is unambiguous, and its unusualness in this
     * codebase is a feature: it makes the one exception visible.
     *
     * <p>{@code status = 1} is {@code EntityStatus.ACTIVE} — note the enum is
     * {@code INACTIVE(0), ACTIVE(1), DELETED(2)}, so the intuitive "0 means the
     * normal state" is wrong here and would return only deactivated patients.
     * Inactive and deleted rows must not surface a hospital the patient no
     * longer attends.
     */
    @Query(value = """
        SELECT p.id AS patient_id, p.tenant_id AS tenant_id
        FROM patients p
        WHERE p.contact_number_token = :token
          AND p.status = 1
        """, nativeQuery = true)
    List<PortalPatientIdProjection> findIdsByContactNumberTokenAcrossTenants(
        @Param("token") String token);

    /** Spring Data projection: two ids, nothing else. */
    interface PortalPatientIdProjection {
        UUID getPatientId();

        UUID getTenantId();
    }
}
