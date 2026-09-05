package com.hms.infrastructure.persistence.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface MfaChallengeJpaRepository extends JpaRepository<MfaChallengeEntity, UUID> {

    /** Housekeeping. An expired or consumed challenge is of no further use. */
    @Modifying
    @Query("DELETE FROM MfaChallengeEntity c WHERE c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
