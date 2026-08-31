package com.hms.infrastructure.persistence.compliance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ErasureTargetJpaRepository extends JpaRepository<ErasureTargetEntity, UUID> {

    List<ErasureTargetEntity> findByRequestIdOrderByTargetStore(UUID requestId);

    /** A PENDING or FAILED target is an incomplete erasure; this is what surfaces it. */
    List<ErasureTargetEntity> findByRequestIdAndOutcomeIn(UUID requestId, List<String> outcomes);
}
