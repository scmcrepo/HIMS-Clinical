package com.hms.infrastructure.persistence.preauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PreAuthEnhancementJpaRepository
        extends JpaRepository<PreAuthEnhancementEntity, UUID> {

    List<PreAuthEnhancementEntity> findByNhcxTransactionIdOrderBySequenceNumberAsc(UUID txnId);

    /** Callback lookup. Indexed and tenant-filtered, unlike scanning findAll(). */
    Optional<PreAuthEnhancementEntity> findByCorrelationId(String correlationId);
}
