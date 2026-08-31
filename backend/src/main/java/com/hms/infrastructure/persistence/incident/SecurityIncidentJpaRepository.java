package com.hms.infrastructure.persistence.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityIncidentJpaRepository extends JpaRepository<SecurityIncidentEntity, UUID> {

    Optional<SecurityIncidentEntity> findByIncidentRef(String incidentRef);

    List<SecurityIncidentEntity> findByStateOrderByDetectedAtDesc(String state);

    List<SecurityIncidentEntity> findAllByOrderByDetectedAtDesc();

    /**
     * Incidents whose Board notification is overdue.
     *
     * <p>Tenant-agnostic: this runs from a scheduled thread, and a platform-wide
     * incident has no tenant at all. An unreported breach must not be invisible
     * because nobody happened to be logged into the right hospital.
     */
    @Query("SELECT i FROM SecurityIncidentEntity i "
         + "WHERE i.boardNotifiedAt IS NULL "
         + "AND i.state NOT IN ('DISMISSED', 'CLOSED') "
         + "AND i.detectedAt < :cutoff")
    List<SecurityIncidentEntity> findBoardNotificationOverdue(@Param("cutoff") Instant cutoff);

    @Query("SELECT i FROM SecurityIncidentEntity i "
         + "WHERE i.boardNotifiedAt IS NOT NULL AND i.boardDetailReportAt IS NULL "
         + "AND i.state NOT IN ('DISMISSED', 'CLOSED') "
         + "AND i.detectedAt < :cutoff")
    List<SecurityIncidentEntity> findDetailReportOverdue(@Param("cutoff") Instant cutoff);

    @Query("SELECT COUNT(i) FROM SecurityIncidentEntity i WHERE i.state = 'OPEN'")
    long countOpen();

    /** Deduplication window for auto-raised incidents. */
    @Query("SELECT i FROM SecurityIncidentEntity i "
         + "WHERE i.category = :category AND i.detectionSource = :source "
         + "AND i.state NOT IN ('CLOSED', 'DISMISSED') AND i.detectedAt > :since")
    List<SecurityIncidentEntity> findRecentOpenByCategory(@Param("category") String category,
                                                          @Param("source") String source,
                                                          @Param("since") Instant since);
}
