package com.hms.infrastructure.persistence.retention;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetentionRunJpaRepository extends JpaRepository<RetentionRunEntity, UUID> {

    List<RetentionRunEntity> findTop20ByOrderByStartedAtDesc();
}
