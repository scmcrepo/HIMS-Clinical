package com.hms.infrastructure.persistence.hitl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HitlEscalationJpaRepository extends JpaRepository<HitlEscalationEntity, UUID> {

    /** The operator queue. Tenant-filtered automatically via AuditableEntity. */
    List<HitlEscalationEntity> findByStateOrderByCreatedAtAsc(String state);

    Optional<HitlEscalationEntity> findByRunIdAndState(String runId, String state);

    long countByState(String state);

    /**
     * Overdue escalations, for the timeout job.
     *
     * <p>Deliberately tenant-agnostic: this runs on a scheduled thread where no
     * tenant context exists and the Hibernate filter is therefore not enabled.
     * The job must sweep every tenant, so that is correct here — do not "fix" it
     * by adding a tenant predicate.
     */
    @Query("SELECT e FROM HitlEscalationEntity e "
         + "WHERE e.state = 'WAITING' AND e.expiresAt < :cutoff")
    List<HitlEscalationEntity> findOverdue(@Param("cutoff") Instant cutoff);
}
