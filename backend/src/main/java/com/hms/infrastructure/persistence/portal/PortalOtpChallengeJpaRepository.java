package com.hms.infrastructure.persistence.portal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalOtpChallengeJpaRepository
        extends JpaRepository<PortalOtpChallengeEntity, UUID> {

    /**
     * The newest unconsumed challenge for a number.
     *
     * <p>Newest-first matters: a patient who taps Resend then types the code
     * from the first SMS should fail, not succeed. Accepting any live challenge
     * would widen the guessing window by the number of resends.
     */
    @Query("""
        SELECT c FROM PortalOtpChallengeEntity c
        WHERE c.contactNumberToken = :token
          AND c.consumedAt IS NULL
        ORDER BY c.issuedAt DESC
        """)
    List<PortalOtpChallengeEntity> findLiveByToken(@Param("token") String token);

    default Optional<PortalOtpChallengeEntity> findNewestLive(String token) {
        return findLiveByToken(token).stream().findFirst();
    }

    /** Rate limiting: how many codes has this number been sent lately. */
    @Query("""
        SELECT COUNT(c) FROM PortalOtpChallengeEntity c
        WHERE c.contactNumberToken = :token
          AND c.issuedAt > :since
        """)
    long countIssuedSince(@Param("token") String token, @Param("since") Instant since);

    /** Rate limiting: how many codes has this source been asking for. */
    @Query("""
        SELECT COUNT(c) FROM PortalOtpChallengeEntity c
        WHERE c.sourceHash = :sourceHash
          AND c.issuedAt > :since
        """)
    long countIssuedBySourceSince(
        @Param("sourceHash") String sourceHash, @Param("since") Instant since);

    /**
     * Retention. These are authentication artefacts, not records — nothing is
     * served by keeping them, and each one is a row tying a phone-number
     * fingerprint to a timestamp.
     */
    @Modifying
    @Query("DELETE FROM PortalOtpChallengeEntity c WHERE c.expiresAt < :cutoff")
    int purgeExpiredBefore(@Param("cutoff") Instant cutoff);
}
