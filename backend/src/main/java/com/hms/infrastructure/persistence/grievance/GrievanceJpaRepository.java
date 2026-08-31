package com.hms.infrastructure.persistence.grievance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GrievanceJpaRepository extends JpaRepository<GrievanceEntity, UUID> {

    Optional<GrievanceEntity> findByGrievanceRef(String grievanceRef);

    List<GrievanceEntity> findByStateOrderByDueAtAsc(String state);

    List<GrievanceEntity> findAllByOrderByReceivedAtDesc();

    List<GrievanceEntity> findByPatientIdOrderByReceivedAtDesc(UUID patientId);

    /**
     * Open grievances past the statutory deadline.
     *
     * <p>Tenant-agnostic: this runs from a scheduled thread with no tenant
     * context. An overdue complaint must not stay invisible because nobody
     * happened to be logged into that hospital — which is exactly how a 90-day
     * ceiling gets breached.
     */
    @Query("SELECT g FROM GrievanceEntity g "
         + "WHERE g.state NOT IN ('RESOLVED', 'CLOSED', 'WITHDRAWN') AND g.dueAt < :now")
    List<GrievanceEntity> findOverdue(@Param("now") Instant now);

    /** Approaching the deadline but not yet past it — the useful warning. */
    @Query("SELECT g FROM GrievanceEntity g "
         + "WHERE g.state NOT IN ('RESOLVED', 'CLOSED', 'WITHDRAWN') "
         + "AND g.dueAt >= :now AND g.targetAt < :now")
    List<GrievanceEntity> findPastTarget(@Param("now") Instant now);

    /** Never acknowledged. A complaint nobody replied to is the worst of them. */
    @Query("SELECT g FROM GrievanceEntity g "
         + "WHERE g.acknowledgedAt IS NULL "
         + "AND g.state NOT IN ('RESOLVED', 'CLOSED', 'WITHDRAWN') "
         + "AND g.receivedAt < :cutoff")
    List<GrievanceEntity> findUnacknowledged(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(g) FROM GrievanceEntity g "
         + "WHERE g.state NOT IN ('RESOLVED', 'CLOSED', 'WITHDRAWN')")
    long countOpen();

    @Query("SELECT COUNT(g) FROM GrievanceEntity g WHERE g.escalatedToBoard = true")
    long countEscalated();
}
