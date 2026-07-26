package com.hms.infrastructure.persistence.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AgentIdempotencyKeyJpaRepository
        extends JpaRepository<AgentIdempotencyKeyEntity, UUID> {

    Optional<AgentIdempotencyKeyEntity> findByKeyHash(String keyHash);

    /**
     * Purge expired keys.
     *
     * <p>Tenant-agnostic on purpose: this runs from a scheduled job on a thread
     * with no tenant context, so it must not rely on the Hibernate tenant filter.
     * The predicate is time-based only and touches no patient data.
     */
    @Modifying
    @Query("DELETE FROM AgentIdempotencyKeyEntity k WHERE k.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
