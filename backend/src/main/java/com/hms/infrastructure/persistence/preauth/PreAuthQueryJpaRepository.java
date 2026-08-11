package com.hms.infrastructure.persistence.preauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreAuthQueryJpaRepository extends JpaRepository<PreAuthQueryEntity, UUID> {

    List<PreAuthQueryEntity> findByNhcxTransactionIdOrderByRoundNumberAsc(UUID nhcxTransactionId);

    /** The desk's work queue: what the insurer is still waiting on. */
    List<PreAuthQueryEntity> findByRespondedAtIsNullOrderByRaisedAtAsc();

    Optional<PreAuthQueryEntity> findByNhcxTransactionIdAndRoundNumber(UUID txnId, Integer round);
}
