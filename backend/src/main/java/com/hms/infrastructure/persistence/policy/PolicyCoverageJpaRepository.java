package com.hms.infrastructure.persistence.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyCoverageJpaRepository extends JpaRepository<PolicyCoverageEntity, UUID> {

    /** Newest first — Screen 2.1 shows the latest check, history is the audit trail. */
    List<PolicyCoverageEntity> findByPatientIdOrderByCheckedAtDesc(UUID patientId);

    List<PolicyCoverageEntity> findByEncounterIdOrderByCheckedAtDesc(UUID encounterId);

    Optional<PolicyCoverageEntity> findByCorrelationId(String correlationId);
}
