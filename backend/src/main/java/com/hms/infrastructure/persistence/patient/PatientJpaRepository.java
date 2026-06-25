package com.hms.infrastructure.persistence.patient;

import com.hms.domain.patient.model.Patient;
import com.hms.domain.shared.model.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Patient repository.
 *
 * ENCRYPTION IMPACT:
 *   firstName, lastName, contactNumber are AES-256-GCM encrypted — LIKE/= on those
 *   columns is no longer possible in SQL.
 *
 * Search strategy after encryption:
 *   1. By patient number (number_sequence, unencrypted) — primary key for staff.
 *   2. By HMAC contact token (contact_number_token column) — exact phone lookup.
 *   3. By UUID — internal links (always preferred).
 *   4. Name search — load recent active patients, filter in Java via PatientSearchService.
 *
 *   See PatientSearchService for the application-layer name matching logic.
 */
public interface PatientJpaRepository extends JpaRepository<Patient, UUID> {

    /**
     * Search by patient number only — safe because number_sequence is not PII.
     * Used as the primary DB-side search; name filtering is applied in PatientSearchService.
     */
    @Query("""
        SELECT p FROM Patient p
        WHERE p.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND
              EXISTS (SELECT 1 FROM NumberSequenceEntity ns WHERE ns.id = p.id
                      AND LOWER(ns.value) LIKE LOWER(CONCAT('%',:q,'%')))
        """)
    Page<Patient> searchByPatientNumber(@Param("q") String query, Pageable pageable);

    /**
     * Exact phone lookup via HMAC token — O(1) indexed lookup, no decryption needed.
     * The token is computed by PiiSearchTokenService.phoneToken(rawPhone).
     */
    @Query("""
        SELECT p FROM Patient p
        WHERE p.status = com.hms.domain.shared.model.EntityStatus.ACTIVE
          AND p.contactNumberToken = :token
        """)
    List<Patient> findByContactNumberToken(@Param("token") String token);

    /**
     * Returns a page of recent active patients for in-memory name filtering.
     * PatientSearchService fetches this page and filters by decrypted name in Java.
     * Page size should be kept small (≤ 200) to avoid decrypting the whole table.
     */
    @Query("SELECT p FROM Patient p WHERE p.status = com.hms.domain.shared.model.EntityStatus.ACTIVE")
    Page<Patient> findAllActive(Pageable pageable);

    /**
     * Returns IDs for all active patients matching a patient-number prefix.
     * Used by encounter search queries that need patientId lists.
     */
    @Query("""
        SELECT p.id FROM Patient p
        WHERE p.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND
              EXISTS (SELECT 1 FROM NumberSequenceEntity ns WHERE ns.id = p.id
                      AND LOWER(ns.value) LIKE LOWER(CONCAT('%',:q,'%')))
        """)
    List<UUID> searchIdsByPatientNumber(@Param("q") String query);

    /**
     * Exact UUID lookup — overridden to force JPQL and apply Hibernate filters (preventing CrossTenantAccessException on direct key loads).
     */
    @Override
    @Query("SELECT p FROM Patient p WHERE p.id = :id")
    Optional<Patient> findById(@Param("id") UUID id);
}
