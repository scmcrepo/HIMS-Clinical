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

/**
 * ClinicalEncounter repository.
 *
 * ENCRYPTION IMPACT:
 *   Patient.firstName, lastName, contactNumber are AES-encrypted.
 *   All LIKE searches on those fields have been removed.
 *
 * Search strategy (post-encryption):
 *   - Filtered queries (date, consultant, status) still use SQL — efficient.
 *   - Text search (:q param) is limited to patient NUMBER (number_sequence) only.
 *     Name / phone text search must be done by the service layer using PatientSearchService
 *     to get matching patient IDs first, then filtering by id IN (:ids).
 *
 * For all search* methods: if :q is non-empty and not a patient-number-like string,
 *   callers should pre-resolve it to a List<UUID> via PatientSearchService
 *   and use the findBy*ForPatients overloads below.
 */
public interface ClinicalEncounterJpaRepository extends JpaRepository<ClinicalEncounter, UUID> {

    // ── OP queue (filtered, with optional text search by patient number) ──────

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, com.hms.domain.patient.model.Patient p, com.hms.infrastructure.sequence.NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id " +
           "AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT " +
           "AND (:dateSpecified = false AND e.encounterStatus <> com.hms.domain.encounter.model.EncounterStatus.BILLING_DONE OR :dateSpecified = true AND e.startedAt >= :start AND e.startedAt < :end) " +
           "AND (:hasSecDepartments = false OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId IN :secDepartmentIds)) " +
           "AND (:consultantId IS NULL OR e.primaryProviderId = :consultantId) " +
           "AND (:status IS NULL OR e.encounterStatus = :status) " +
           "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%'))")
    Page<ClinicalEncounter> searchOutpatientsFiltered(
            @Param("q") String query,
            @Param("dateSpecified") boolean dateSpecified,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("consultantId") UUID consultantId,
            @Param("status") EncounterStatus status,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("hasSecDepartments") boolean hasSecDepartments,
            @Param("secDepartmentIds") Collection<UUID> secDepartmentIds,
            Pageable pageable);

    /** Filtered OP query pre-resolved to a specific set of patient IDs (for name/phone search). */
    @Query("SELECT DISTINCT e FROM ClinicalEncounter e " +
           "WHERE e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT " +
           "AND e.patientId IN :patientIds " +
           "AND (:dateSpecified = false AND e.encounterStatus <> com.hms.domain.encounter.model.EncounterStatus.BILLING_DONE OR :dateSpecified = true AND e.startedAt >= :start AND e.startedAt < :end) " +
           "AND (:hasSecDepartments = false OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId IN :secDepartmentIds)) " +
           "AND (:consultantId IS NULL OR e.primaryProviderId = :consultantId) " +
           "AND (:status IS NULL OR e.encounterStatus = :status)")
    Page<ClinicalEncounter> searchOutpatientsForPatients(
            @Param("patientIds") Collection<UUID> patientIds,
            @Param("dateSpecified") boolean dateSpecified,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("consultantId") UUID consultantId,
            @Param("status") EncounterStatus status,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("hasSecDepartments") boolean hasSecDepartments,
            @Param("secDepartmentIds") Collection<UUID> secDepartmentIds,
            Pageable pageable);

    // ── Basic lookups (unchanged — no PII fields) ─────────────────────────────

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.encounterType = :type AND e.cancelled = false ORDER BY e.startedAt DESC")
    List<ClinicalEncounter> findByPatientIdAndType(@Param("pid") UUID patientId, @Param("type") EncounterType type);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findByPatientIdPaged(@Param("pid") UUID patientId, Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.patientId = :pid AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    List<ClinicalEncounter> findActiveInpatientByPatientId(@Param("pid") UUID patientId);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findActiveInpatientsPaged(Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false " +
           "AND (:hasSecDepartments = false OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId IN :secDepartmentIds)) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findActiveInpatientsPagedSecured(
            @Param("secConsultantId") UUID secConsultantId,
            @Param("hasSecDepartments") boolean hasSecDepartments,
            @Param("secDepartmentIds") Collection<UUID> secDepartmentIds,
            Pageable pageable);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL AND e.cancelled = false ORDER BY e.startedAt DESC")
    List<ClinicalEncounter> findActiveInpatients();

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.cancelled = false AND e.startedAt >= :cutoff ORDER BY e.startedAt DESC")
    List<ClinicalEncounter> findRecentOutpatients(@Param("cutoff") Instant cutoff);

    @Query("SELECT e FROM ClinicalEncounter e WHERE e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.startedAt >= :startOfDay AND e.cancelled = false " +
           "AND (:secDepartmentId IS NULL OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId = :secDepartmentId)) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findTodayOutpatients(
            @Param("startOfDay") Instant startOfDay,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("secDepartmentId") UUID secDepartmentId,
            Pageable pageable);

    // ── Date-range searches (patient-number text filter only) ─────────────────

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, com.hms.domain.patient.model.Patient p, com.hms.infrastructure.sequence.NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT " +
           "AND e.startedAt >= :start AND e.startedAt < :end " +
           "AND (:hasSecDepartments = false OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId IN :secDepartmentIds)) " +
           "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchOutpatientsByDate(
            @Param("q") String query,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("hasSecDepartments") boolean hasSecDepartments,
            @Param("secDepartmentIds") Collection<UUID> secDepartmentIds,
            Pageable pageable);

    @Query("SELECT COUNT(e) FROM ClinicalEncounter e WHERE e.primaryProviderId = :pid AND CAST(e.startedAt AS date) = CURRENT_DATE AND e.cancelled = false")
    long countTodayByProvider(@Param("pid") UUID providerId);

    // ── IP searches ───────────────────────────────────────────────────────────

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, com.hms.domain.patient.model.Patient p, com.hms.infrastructure.sequence.NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT AND e.dischargedAt IS NULL " +
           "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchActiveInpatients(@Param("q") String query, Pageable pageable);

    /** IP search pre-resolved to patient IDs. */
    @Query("SELECT DISTINCT e FROM ClinicalEncounter e " +
           "WHERE e.cancelled = false AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT " +
           "AND e.dischargedAt IS NULL AND e.patientId IN :patientIds " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchActiveInpatientsForPatients(
            @Param("patientIds") Collection<UUID> patientIds, Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, com.hms.domain.patient.model.Patient p, com.hms.infrastructure.sequence.NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.OUTPATIENT AND e.startedAt >= :startOfDay " +
           "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchTodayOutpatients(@Param("q") String query, @Param("startOfDay") Instant startOfDay, Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, com.hms.domain.patient.model.Patient p, com.hms.infrastructure.sequence.NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id AND e.cancelled = false " +
           "AND (:hasSecDepartments = false OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId IN :secDepartmentIds)) " +
           "AND e.startedAt >= :start AND e.startedAt < :end " +
           "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%')) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> searchAllWithDate(
            @Param("q") String query,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("hasSecDepartments") boolean hasSecDepartments,
            @Param("secDepartmentIds") Collection<UUID> secDepartmentIds,
            Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, com.hms.domain.patient.model.Patient p, com.hms.infrastructure.sequence.NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id AND e.cancelled = false " +
           "AND (:hasSecDepartments = false OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId IN :secDepartmentIds)) " +
           "AND e.startedAt >= :start AND e.startedAt < :end " +
           "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY e.startedAt DESC")
    Page<ClinicalEncounter> findAllWithDate(
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("q") String query,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("hasSecDepartments") boolean hasSecDepartments,
            @Param("secDepartmentIds") Collection<UUID> secDepartmentIds,
            Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e, com.hms.domain.patient.model.Patient p, com.hms.infrastructure.sequence.NumberSequenceEntity n " +
           "WHERE e.patientId = p.id AND e.patientId = n.id AND e.cancelled = false " +
           "AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT " +
           "AND (:consultantId IS NULL OR e.primaryProviderId = :consultantId) " +
           "AND (:hasSecDepartments = false OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId IN :secDepartmentIds)) " +
           "AND (:dateSpecified = false OR (e.startedAt >= :start AND e.startedAt < :end)) " +
           "AND (:activeOnly = false OR e.dischargedAt IS NULL) " +
           "AND (:statusFilter IS NULL OR (:statusFilter = 'ADMITTED' AND e.dischargedAt IS NULL) OR (:statusFilter = 'DISCHARGED' AND e.dischargedAt IS NOT NULL)) " +
           "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')) OR CAST(e.patientId AS string) LIKE CONCAT('%', :q, '%'))")
    Page<ClinicalEncounter> searchInpatientsFiltered(
            @Param("q") String query,
            @Param("consultantId") UUID consultantId,
            @Param("dateSpecified") boolean dateSpecified,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("activeOnly") boolean activeOnly,
            @Param("statusFilter") String statusFilter,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("hasSecDepartments") boolean hasSecDepartments,
            @Param("secDepartmentIds") Collection<UUID> secDepartmentIds,
            Pageable pageable);

    @Query("SELECT DISTINCT e FROM ClinicalEncounter e " +
           "WHERE e.cancelled = false AND e.encounterType = com.hms.domain.billing.model.EncounterType.INPATIENT " +
           "AND e.patientId IN :patientIds " +
           "AND (:consultantId IS NULL OR e.primaryProviderId = :consultantId) " +
           "AND (:hasSecDepartments = false OR e.primaryProviderId = :secConsultantId OR e.primaryProviderId IN (SELECT c.id FROM com.hms.domain.consultant.model.Consultant c WHERE c.departmentId IN :secDepartmentIds)) " +
           "AND (:dateSpecified = false OR (e.startedAt >= :start AND e.startedAt < :end)) " +
           "AND (:activeOnly = false OR e.dischargedAt IS NULL) " +
           "AND (:statusFilter IS NULL OR (:statusFilter = 'ADMITTED' AND e.dischargedAt IS NULL) OR (:statusFilter = 'DISCHARGED' AND e.dischargedAt IS NOT NULL))")
    Page<ClinicalEncounter> searchInpatientsForPatientsFiltered(
            @Param("patientIds") Collection<UUID> patientIds,
            @Param("consultantId") UUID consultantId,
            @Param("dateSpecified") boolean dateSpecified,
            @Param("start") Instant start,
            @Param("end") Instant end,
            @Param("activeOnly") boolean activeOnly,
            @Param("statusFilter") String statusFilter,
            @Param("secConsultantId") UUID secConsultantId,
            @Param("hasSecDepartments") boolean hasSecDepartments,
            @Param("secDepartmentIds") Collection<UUID> secDepartmentIds,
            Pageable pageable);

    // ── Admission requests (patient-number filter only, native query retained) ─

    @Query(value =
            "SELECT e.* FROM clinical_encounters e " +
            "JOIN patients p ON e.patient_id = p.id " +
            "LEFT JOIN number_sequences n ON e.patient_id = n.id " +
            "WHERE e.tenant_id = CAST(:tenantId AS uuid) " +
            "AND (:branchId IS NULL OR e.branch_id = CAST(:branchId AS uuid)) " +
            "AND e.encounter_type = 0 AND e.is_cancelled = false AND e.started_at >= :cutoff " +
            "AND e.consultant_share_map IS NOT NULL " +
            "AND e.consultant_share_map->'ADMISSION_REQUEST'->>'status' = 'REQUESTED' " +
            "AND (:consultantId IS NULL OR e.primary_provider_id = CAST(:consultantId AS uuid)) " +
            "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')))",
            countQuery =
            "SELECT COUNT(e.id) FROM clinical_encounters e " +
            "LEFT JOIN number_sequences n ON e.patient_id = n.id " +
            "WHERE e.tenant_id = CAST(:tenantId AS uuid) " +
            "AND (:branchId IS NULL OR e.branch_id = CAST(:branchId AS uuid)) " +
            "AND e.encounter_type = 0 AND e.is_cancelled = false AND e.started_at >= :cutoff " +
            "AND e.consultant_share_map IS NOT NULL " +
            "AND e.consultant_share_map->'ADMISSION_REQUEST'->>'status' = 'REQUESTED' " +
            "AND (:consultantId IS NULL OR e.primary_provider_id = CAST(:consultantId AS uuid)) " +
            "AND (:q IS NULL OR :q = '' OR LOWER(n.value) LIKE LOWER(CONCAT('%', :q, '%')))",
            nativeQuery = true)
    Page<ClinicalEncounter> findPendingAdmissionRequestsPaged(
            @Param("cutoff") Instant cutoff,
            @Param("q") String query,
            @Param("consultantId") UUID consultantId,
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            Pageable pageable);
}
