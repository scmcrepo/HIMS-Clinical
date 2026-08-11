package com.hms.infrastructure.persistence.preauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PreAuthEstimateLineJpaRepository
        extends JpaRepository<PreAuthEstimateLineEntity, UUID> {

    List<PreAuthEstimateLineEntity> findByNhcxTransactionId(UUID nhcxTransactionId);
}
