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
    Optional<Consultant> findByUserId(UUID userId);

    /**
     * Duplicate contact check via HMAC token.
     * Token is maintained by ConsultantService whenever contact changes.
     * Replaces: existsByContactAndStatusNot()
     */
    boolean existsByContactNumberTokenAndStatusNot(
        String contactNumberToken,
        com.hms.domain.shared.model.EntityStatus status);

    boolean existsByContactNumberTokenAndStatusNotAndIdNot(
        String contactNumberToken,
        com.hms.domain.shared.model.EntityStatus status,
        UUID id);
}
