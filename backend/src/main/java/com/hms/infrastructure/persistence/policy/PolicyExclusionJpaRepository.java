package com.hms.infrastructure.persistence.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicyExclusionJpaRepository extends JpaRepository<PolicyExclusionEntity, UUID> {

    List<PolicyExclusionEntity> findByCoverageId(UUID coverageId);
}
