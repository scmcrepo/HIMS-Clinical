package com.hms.infrastructure.persistence.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimDeductionLineJpaRepository
        extends JpaRepository<ClaimDeductionLineEntity, UUID> {

    List<ClaimDeductionLineEntity> findByNhcxTransactionId(UUID nhcxTransactionId);
}
