package com.hms.infrastructure.persistence.retention;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetentionRunItemJpaRepository extends JpaRepository<RetentionRunItemEntity, UUID> {

    List<RetentionRunItemEntity> findByRunIdOrderByTargetStore(UUID runId);
}
