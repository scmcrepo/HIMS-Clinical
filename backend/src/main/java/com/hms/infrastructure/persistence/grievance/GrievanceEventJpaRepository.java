package com.hms.infrastructure.persistence.grievance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GrievanceEventJpaRepository extends JpaRepository<GrievanceEventEntity, UUID> {

    List<GrievanceEventEntity> findByGrievanceIdOrderByOccurredAtDesc(UUID grievanceId);
}
