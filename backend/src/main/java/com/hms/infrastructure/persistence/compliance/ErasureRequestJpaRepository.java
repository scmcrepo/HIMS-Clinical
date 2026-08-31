package com.hms.infrastructure.persistence.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant scope comes from the Hibernate {@code tenantFilter}, as for every other
 * {@code AuditableEntity} repository. {@link #findOverdue} is the one exception
 * and says so.
 */
public interface ErasureRequestJpaRepository extends JpaRepository<ErasureRequestEntity, UUID> {

    List<ErasureRequestEntity> findByPatientIdOrderByRequestedAtDesc(UUID patientId);

    List<ErasureRequestEntity> findByStateOrderByRequestedAtAsc(String state);

    /** An open request already on file, so a second ask does not open a duplicate. */
    @Query("SELECT r FROM ErasureRequestEntity r WHERE r.patientId = :patientId "
         + "AND r.requestType = :requestType AND r.state IN ('RECEIVED', 'IN_PROGRESS')")
    Optional<ErasureRequestEntity> findOpenFor(@Param("patientId") UUID patientId,
                                               @Param("requestType") String requestType);

    /**
     * Requests past their statutory deadline.
     *
     * <p>Tenant-agnostic on purpose: this runs from a scheduled thread with no
     * tenant context, and an overdue rights request is exactly the thing that
     * must not be invisible because nobody happened to be logged into that
     * hospital.
     */
    @Query("SELECT r FROM ErasureRequestEntity r WHERE r.state IN ('RECEIVED', 'IN_PROGRESS') "
         + "AND r.dueAt IS NOT NULL AND r.dueAt < :now")
    List<ErasureRequestEntity> findOverdue(@Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM ErasureRequestEntity r "
         + "WHERE r.state IN ('RECEIVED', 'IN_PROGRESS')")
    long countOpen();
}
