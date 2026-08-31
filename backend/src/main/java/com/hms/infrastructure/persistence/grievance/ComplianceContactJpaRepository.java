package com.hms.infrastructure.persistence.grievance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceContactJpaRepository extends JpaRepository<ComplianceContactEntity, UUID> {

    /** The contact currently published for this tenant, if any. */
    Optional<ComplianceContactEntity> findFirstByActiveToIsNull();

    /**
     * Lookup by explicit tenant, for the unauthenticated publication endpoint.
     *
     * <p>Deliberately tenant-parameterised rather than relying on
     * {@code TenantContext}: this is served without a session, so there is no
     * context to rely on. Safe because the record exists to be published — it is
     * organisational contact information, not personal data. This is the one
     * table in the system where a cross-tenant read is the intended behaviour.
     */
    @Query("SELECT c FROM ComplianceContactEntity c "
         + "WHERE c.tenantId = :tenantId AND c.activeTo IS NULL AND c.status = 1")
    Optional<ComplianceContactEntity> findPublishedFor(@Param("tenantId") UUID tenantId);

    /** Tenants with no published contact — an s. 8(9) gap, per tenant. */
    @Query("SELECT DISTINCT c.tenantId FROM ComplianceContactEntity c WHERE c.activeTo IS NULL")
    List<UUID> tenantsWithPublishedContact();
}
