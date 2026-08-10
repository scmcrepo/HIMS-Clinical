package com.hms.infrastructure.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimPaymentAdviceJpaRepository
        extends JpaRepository<ClaimPaymentAdviceEntity, UUID> {

    /** Duplicate-advice guard. The DB also enforces this per tenant. */
    Optional<ClaimPaymentAdviceEntity> findByUtrNumber(String utrNumber);

    List<ClaimPaymentAdviceEntity> findByNhcxTransactionId(UUID nhcxTransactionId);

    /** The accounts team's work queue. */
    List<ClaimPaymentAdviceEntity> findByReconciledFalseOrderByPaymentDateAsc();
}
