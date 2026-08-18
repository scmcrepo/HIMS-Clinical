package com.hms.infrastructure.persistence.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalSessionJpaRepository extends JpaRepository<PortalSessionEntity, UUID> {

    /**
     * Looked up on every refresh. Returns the row whatever its state — consumed
     * and revoked rows must come back, because "this token exists and is
     * already spent" is the reuse signal, and a query that filtered them out
     * would report it as an unknown token instead.
     */
    Optional<PortalSessionEntity> findByRefreshTokenHash(String refreshTokenHash);

    List<PortalSessionEntity> findByChainId(UUID chainId);

    /**
     * Live chains for a patient, newest first. Backs the two-device cap:
     * a third login revokes the oldest rather than being refused, because
     * refusing would strand a patient who lost a phone.
     */
    @Query("""
        SELECT s FROM PortalSessionEntity s
        WHERE s.patientId = :patientId
          AND s.consumedAt IS NULL
          AND s.revokedAt IS NULL
          AND s.expiresAt > :now
        ORDER BY s.issuedAt DESC
        """)
    List<PortalSessionEntity> findLiveByPatient(
        @Param("patientId") UUID patientId, @Param("now") Instant now);

    @Modifying
    @Query("""
        UPDATE PortalSessionEntity s
        SET s.revokedAt = :now, s.revokedReason = :reason
        WHERE s.chainId = :chainId AND s.revokedAt IS NULL
        """)
    int revokeChain(
        @Param("chainId") UUID chainId,
        @Param("now") Instant now,
        @Param("reason") String reason);

    /**
     * Erasure reachability. WO-017 §5 — a DPDP erasure request has to reach
     * every copy, and a session row ties a patient id to a device and a set of
     * timestamps.
     */
    @Modifying
    @Query("DELETE FROM PortalSessionEntity s WHERE s.patientId = :patientId")
    int deleteAllForPatient(@Param("patientId") UUID patientId);

    /** Retention: 30 days past expiry. */
    @Modifying
    @Query("DELETE FROM PortalSessionEntity s WHERE s.expiresAt < :cutoff")
    int purgeExpiredBefore(@Param("cutoff") Instant cutoff);
}
