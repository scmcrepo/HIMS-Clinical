package com.hms.infrastructure.persistence.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Tenant-scoped by the Hibernate filter. Search tokens, never raw policy numbers. */
public interface DiscoveredPolicyJpaRepository extends JpaRepository<DiscoveredPolicyEntity, UUID> {

    List<DiscoveredPolicyEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<DiscoveredPolicyEntity> findByCorrelationId(String correlationId);

    List<DiscoveredPolicyEntity> findByPolicyNumberToken(String policyNumberToken);
}
