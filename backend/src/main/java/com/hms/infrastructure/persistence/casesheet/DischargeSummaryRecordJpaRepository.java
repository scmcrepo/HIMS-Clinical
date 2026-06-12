package com.hms.infrastructure.persistence.casesheet;

import com.hms.domain.casesheet.model.DischargeSummaryRecord;
import com.hms.domain.shared.model.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DischargeSummaryRecordJpaRepository extends JpaRepository<DischargeSummaryRecord, UUID> {

    @Query("""
            SELECT r FROM DischargeSummaryRecord r JOIN FETCH r.template
            WHERE r.encounterId = :eid AND r.status = :status
            ORDER BY r.recordedAt DESC
            """)
    List<DischargeSummaryRecord> findByEncounterIdAndStatus(
            @Param("eid") UUID encounterId, @Param("status") EntityStatus status);

    @Query("""
            SELECT r FROM DischargeSummaryRecord r JOIN FETCH r.template
            WHERE r.id = :id
            """)
    Optional<DischargeSummaryRecord> findByIdWithTemplate(@Param("id") UUID id);

    @Query("""
            SELECT r FROM DischargeSummaryRecord r JOIN FETCH r.template
            WHERE r.encounterId = :eid AND r.template.id = :tid AND r.status = :status
            """)
    Optional<DischargeSummaryRecord> findByEncounterIdAndTemplateIdAndStatus(
            @Param("eid") UUID encounterId,
            @Param("tid") UUID templateId,
            @Param("status") EntityStatus status);

    boolean existsByEncounterIdAndStatus(UUID encounterId, EntityStatus status);
}
