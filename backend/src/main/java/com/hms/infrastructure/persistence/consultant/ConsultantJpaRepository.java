package com.hms.infrastructure.persistence.consultant;

import com.hms.domain.consultant.model.Consultant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

/**
 * Consultant repository.
 *
 * ENCRYPTION IMPACT:
 *   firstName and lastName are AES-encrypted — LIKE searches on those columns are broken.
 *   searchByName / searchNonDeletedByName now return ALL consultants so that
 *   ConsultantService can filter by decrypted name in Java.
 *
 *   For contact-number duplicate checks, use contactNumberToken (HMAC) instead of
 *   direct contact equality — see ConsultantService.
 */
public interface ConsultantJpaRepository extends JpaRepository<Consultant, UUID> {

    /** All active consultants. Name filtering must be done in application layer. */
    @Query("SELECT c FROM Consultant c WHERE c.status = 1 ORDER BY c.status")
    List<Consultant> findAllActive();

    /**
     * Returns ALL active consultants for in-memory name filtering.
     * Callers should filter the result list by decrypted firstName/lastName.
     */
    @Query("SELECT c FROM Consultant c WHERE c.status = 1")
    List<Consultant> findAllActiveForNameSearch();

    /** Returns all non-deleted consultants for in-memory search. */
    @Query("SELECT c FROM Consultant c WHERE c.status != com.hms.domain.shared.model.EntityStatus.DELETED ORDER BY c.status DESC")
    List<Consultant> findAllNonDeleted();

    /** Lookup by associated user ID — unchanged (UUID, not PII). */
    List<Consultant> findByUserId(UUID userId);

    Optional<Consultant> findByUserIdAndBranchId(UUID userId, UUID branchId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Consultant c SET c.firstName = :firstName, c.lastName = :lastName, c.salutation = :salutation, c.email = :email, c.contact = :contact, c.contactNumberToken = :contactNumberToken, c.status = 1 WHERE c.userId = :userId AND c.branchId = :branchId")
    int updateProfileForBranch(
        @Param("userId") UUID userId,
        @Param("branchId") UUID branchId,
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("salutation") String salutation,
        @Param("email") String email,
        @Param("contact") String contact,
        @Param("contactNumberToken") String contactNumberToken
    );

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Consultant c SET c.firstName = :firstName, c.lastName = :lastName, c.salutation = :salutation, c.email = :email, c.contact = :contact, c.contactNumberToken = :contactNumberToken WHERE c.userId = :userId")
    void updateProfileDetails(
        @Param("userId") UUID userId,
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("salutation") String salutation,
        @Param("email") String email,
        @Param("contact") String contact,
        @Param("contactNumberToken") String contactNumberToken
    );

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Consultant c SET c.status = com.hms.domain.shared.model.EntityStatus.DELETED WHERE c.userId = :userId AND c.branchId NOT IN :branchIds")
    void deleteConsultantsForBranchesNotIn(
        @Param("userId") UUID userId,
        @Param("branchIds") Collection<UUID> branchIds
    );

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Consultant c SET c.status = com.hms.domain.shared.model.EntityStatus.DELETED WHERE c.userId = :userId")
    void deleteAllConsultantsForUser(@Param("userId") UUID userId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Consultant c SET c.status = com.hms.domain.shared.model.EntityStatus.DELETED WHERE c.userId = :userId AND c.branchId = :branchId")
    void deleteConsultantForUserInBranch(
        @Param("userId") UUID userId,
        @Param("branchId") UUID branchId
    );

    boolean existsByContactNumberTokenAndStatusNot(
        String contactNumberToken,
        com.hms.domain.shared.model.EntityStatus status);

    boolean existsByContactNumberTokenAndStatusNotAndIdNot(
        String contactNumberToken,
        com.hms.domain.shared.model.EntityStatus status,
        UUID id);

    @Query("SELECT COUNT(c) > 0 FROM Consultant c WHERE c.contactNumberToken = :contactNumberToken AND c.branchId = :branchId AND c.status != :status")
    boolean existsByContactNumberTokenAndBranchIdAndStatusNot(
        @Param("contactNumberToken") String contactNumberToken,
        @Param("branchId") UUID branchId,
        @Param("status") com.hms.domain.shared.model.EntityStatus status);

    @Query("SELECT COUNT(c) > 0 FROM Consultant c WHERE c.contactNumberToken = :contactNumberToken AND c.branchId = :branchId AND c.status != :status AND c.id != :id")
    boolean existsByContactNumberTokenAndBranchIdAndStatusNotAndIdNot(
        @Param("contactNumberToken") String contactNumberToken,
        @Param("branchId") UUID branchId,
        @Param("status") com.hms.domain.shared.model.EntityStatus status,
        @Param("id") UUID id);
}
