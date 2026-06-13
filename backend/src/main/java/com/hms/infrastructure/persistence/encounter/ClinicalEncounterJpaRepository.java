package com.hms.infrastructure.persistence.encounter;

import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.domain.encounter.model.EncounterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicalEncounterJpaRepository extends JpaRepository<ClinicalEncounter, UUID> {

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT " +
           "AND e.startedAt >= :start AND e.startedAt < :end " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "AND (:consultantId IS NULL OR e.primaryProviderId = :consultantId) " +
           "AND (:status IS NULL OR e.encounterStatus = :status) " +
           "AND (:q IS NULL OR :q = '' " +
           "  OR LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "  OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "  OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchOutpatientsFiltered(
            @Param("q") String query,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("consultantId") UUID consultantId,
            @Param("status") EncounterStatus status,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.encounterType = :type AND e.cancelled = false ORDER BY e.startedAt DESC")
    List<ClinicalEncounter> findByPatientIdAndType(@Param("pid") UUID patientId, @Param("type") EncounterType type);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findByPatientIdPaged(@Param("pid") UUID patientId, Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    List<ClinicalEncounter> findActiveInpatientByPatientId(@Param("pid") UUID patientId);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findActiveInpatientsPaged(Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findActiveInpatientsPagedSecured(
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    java.util.List<ClinicalEncounter> findActiveInpatients();

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.cancelled = false AND e.startedAt >= :cutoff ORDER BY e.startedAt DESC")
    java.util.List<ClinicalEncounter> findRecentOutpatients(@Param("cutoff") Instant cutoff);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.startedAt >= :startOfDay AND e.cancelled = false " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findTodayOutpatients(
            @Param("startOfDay") Instant startOfDay,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.startedAt >= :start AND e.startedAt < :end AND e.cancelled = false " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findOutpatientsByDate(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT " +
           "AND e.startedAt >= :start AND e.startedAt < :end " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchOutpatientsByDate(
            @Param("q") String query,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query("SELECT COUNT(e) FROM ClinicalEncounter e WHERE e.primaryProviderId = :pid AND CAST(e.startedAt AS date) = CURRENT_DATE AND e.cancelled = false")
    long countTodayByProvider(@Param("pid") UUID providerId);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchAll(
            @Param("q") String query,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.cancelled = false " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId))")
    Page<ClinicalEncounter> findAllSecured(
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.startedAt >= :start AND e.startedAt < :end AND e.cancelled = false " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findAllWithDate(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, Patient p, NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.startedAt >= :start AND e.startedAt < :end " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "AND (LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR p.contactNumber LIKE CONCAT('%', :q, '%') " +
           "OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchAllWithDate(
            @Param("q") String query,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

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
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM Consultant c WHERE c.departmentId = :secDepartmentId)) " +
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
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    @Query(value =
            "SELECT e.* FROM clinical_encounters e " +
            "JOIN patients p ON e.patient_id = p.id " +
            "LEFT JOIN number_sequences n ON e.patient_id = n.id " +
            "WHERE e.encounter_type = 0 " +
            "AND e.is_cancelled = false " +
            "AND e.started_at >= :cutoff " +
            "AND e.consultant_share_map IS NOT NULL " +
            "AND e.consultant_share_map->'ADMISSION_REQUEST'->>'status' = 'REQUESTED' " +
            "AND (:consultantId IS NULL OR e.primary_provider_id = CAST(:consultantId AS uuid)) " +
            "AND (:q IS NULL OR :q = '' " +
            "  OR LOWER(p.first_name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "  OR LOWER(p.last_name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "  OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "  OR p.contact_number LIKE CONCAT('%', :q, '%'))",
            countQuery =
            "SELECT COUNT(e.id) FROM clinical_encounters e " +
            "JOIN patients p ON e.patient_id = p.id " +
            "LEFT JOIN number_sequences n ON e.patient_id = n.id " +
            "WHERE e.encounter_type = 0 " +
            "AND e.is_cancelled = false " +
            "AND e.started_at >= :cutoff " +
            "AND e.consultant_share_map IS NOT NULL " +
            "AND e.consultant_share_map->'ADMISSION_REQUEST'->>'status' = 'REQUESTED' " +
            "AND (:consultantId IS NULL OR e.primary_provider_id = CAST(:consultantId AS uuid)) " +
            "AND (:q IS NULL OR :q = '' " +
            "  OR LOWER(p.first_name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "  OR LOWER(p.last_name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "  OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "  OR p.contact_number LIKE CONCAT('%', :q, '%'))",
            nativeQuery = true)
    Page<ClinicalEncounter> findPendingAdmissionRequestsPaged(
            @Param("cutoff") Instant cutoff,
            @Param("q") String query,
            @Param("consultantId") UUID consultantId,
            Pageable pageable);
}
