package com.hms.infrastructure.persistence.compliance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordJpaRepository extends JpaRepository<ConsentRecordEntity, UUID> {

    Optional<ConsentRecordEntity> findByPatientIdAndPurposeAndState(
        UUID patientId, String purpose, String state);

    List<ConsentRecordEntity> findByPatientIdOrderByGrantedAtDesc(UUID patientId);

    /**
     * Grants past their expiry, for the sweep that marks them EXPIRED.
     *
     * <p>Tenant-agnostic by design: this runs from a scheduled thread with no
     * tenant context, and consent expiry must be enforced for every tenant.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT c FROM ConsentRecordEntity c WHERE c.state = 'GRANTED' "
        + "AND c.expiresAt IS NOT NULL AND c.expiresAt < :cutoff")
    List<ConsentRecordEntity> findExpired(
        @org.springframework.data.repository.query.Param("cutoff") Instant cutoff);

    /**
     * Live grants carrying a given provenance, for the SYSTEM_INFERRED burndown
     * gauge.
     *
     * <p>Tenant-agnostic for the same reason as {@link #findExpired}: it runs
     * from a scheduled thread with no tenant context, and the number the
     * operator needs is the platform-wide one.
     */
    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(c) FROM ConsentRecordEntity c "
        + "WHERE c.state = 'GRANTED' AND c.provenance = :provenance")
    long countLiveByProvenance(
        @org.springframework.data.repository.query.Param("provenance") String provenance);
}
