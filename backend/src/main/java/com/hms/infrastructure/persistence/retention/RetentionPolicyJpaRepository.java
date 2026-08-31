package com.hms.infrastructure.persistence.retention;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetentionPolicyJpaRepository extends JpaRepository<RetentionPolicyEntity, UUID> {

    Optional<RetentionPolicyEntity> findByTargetStore(String targetStore);

    List<RetentionPolicyEntity> findAllByOrderByTargetStore();

    /**
     * Policies the job will act on, across every tenant.
     *
     * <p>Tenant-agnostic because this runs from a scheduled thread with no
     * tenant context, and each policy carries its own {@code tenantId} which the
     * service uses to scope the actual statement. Retention that only applied to
     * whichever tenant happened to be in context would be no retention at all.
     */
    @Query("SELECT p FROM RetentionPolicyEntity p WHERE p.enabled = true AND p.status = 1 "
         + "ORDER BY p.tenantId, p.targetStore")
    List<RetentionPolicyEntity> findEnabled();

    /** Policies that will actually change data — the ones worth alerting on. */
    @Query("SELECT COUNT(p) FROM RetentionPolicyEntity p "
         + "WHERE p.enabled = true AND p.dryRun = false AND p.status = 1")
    long countLive();
}
