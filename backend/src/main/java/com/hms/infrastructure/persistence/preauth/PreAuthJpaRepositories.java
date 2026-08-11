package com.hms.infrastructure.persistence.preauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Grouped so Module 4's three repositories read together. */
public final class PreAuthJpaRepositories {

    private PreAuthJpaRepositories() {
    }

    public interface EstimateLines extends JpaRepository<PreAuthEstimateLineEntity, UUID> {
        List<PreAuthEstimateLineEntity> findByNhcxTransactionId(UUID nhcxTransactionId);
    }

    public interface Queries extends JpaRepository<PreAuthQueryEntity, UUID> {
        List<PreAuthQueryEntity> findByNhcxTransactionIdOrderByRoundNumberAsc(UUID txnId);

        /** The desk's work queue: what the insurer is still waiting on. */
        List<PreAuthQueryEntity> findByRespondedAtIsNullOrderByRaisedAtAsc();

        Optional<PreAuthQueryEntity> findByNhcxTransactionIdAndRoundNumber(UUID txnId, Integer round);
    }

    public interface Enhancements extends JpaRepository<PreAuthEnhancementEntity, UUID> {
        List<PreAuthEnhancementEntity> findByNhcxTransactionIdOrderBySequenceNumberAsc(UUID txnId);

        /** Callback lookup. Indexed and tenant-filtered, unlike scanning findAll(). */
        Optional<PreAuthEnhancementEntity> findByCorrelationId(String correlationId);
    }
}
