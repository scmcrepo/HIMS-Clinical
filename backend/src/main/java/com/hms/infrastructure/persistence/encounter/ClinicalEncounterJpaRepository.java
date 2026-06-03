package com.hms.infrastructure.persistence.encounter;

import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.encounter.model.ClinicalEncounter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicalEncounterJpaRepository extends JpaRepository<ClinicalEncounter, UUID> {

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.encounterType = :type AND e.cancelled = false ORDER BY e.startedAt DESC")
    List<ClinicalEncounter> findByPatientIdAndType(@Param("pid") UUID patientId, @Param("type") EncounterType type);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findByPatientIdPaged(@Param("pid") UUID patientId, Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    List<ClinicalEncounter> findActiveInpatientByPatientId(@Param("pid") UUID patientId);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findActiveInpatientsPaged(Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    java.util.List<ClinicalEncounter> findActiveInpatients();

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.startedAt >= :startOfDay AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findTodayOutpatients(@Param("startOfDay") Instant startOfDay, Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.startedAt >= :start AND e.startedAt < :end AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findOutpatientsByDate(@Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT " +
           "AND e.startedAt >= :start AND e.startedAt < :end " +
           "AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchOutpatientsByDate(@Param("q") String query, @Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    @Query("SELECT COUNT(e) FROM ClinicalEncounter e WHERE e.primaryProviderId = :pid AND CAST(e.startedAt AS date) = CURRENT_DATE AND e.cancelled = false")
    long countTodayByProvider(@Param("pid") UUID providerId);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchAll(@Param("q") String query, Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.startedAt >= :start AND e.startedAt < :end AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findAllWithDate(@Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.startedAt >= :start AND e.startedAt < :end " +
           "AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchAllWithDate(@Param("q") String query, @Param("start") Instant start, @Param("end") Instant end, Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL " +
           "AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchActiveInpatients(@Param("q") String query, Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.startedAt >= :startOfDay " +
           "AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchTodayOutpatients(@Param("q") String query, @Param("startOfDay") Instant startOfDay, Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT " +
           "AND (:consultantId IS NULL OR e.primaryProviderId = :consultantId) " +
           "AND (:dateSpecified = false AND e.dischargedAt IS NULL OR :dateSpecified = true AND e.startedAt < :end AND (e.dischargedAt IS NULL OR e.dischargedAt >= :start)) " +
           "AND (:q IS NULL OR :q = '' " +
           "  OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "  OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchInpatientsFiltered(
            @Param("q") String query,
            @Param("consultantId") UUID consultantId,
            @Param("dateSpecified") boolean dateSpecified,
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);
}

