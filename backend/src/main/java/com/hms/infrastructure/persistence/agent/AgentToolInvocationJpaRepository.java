package com.hms.infrastructure.persistence.agent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentToolInvocationJpaRepository
        extends JpaRepository<AgentToolInvocationEntity, UUID> {

    Page<AgentToolInvocationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AgentToolInvocationEntity> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);

    List<AgentToolInvocationEntity> findByRunIdOrderByCreatedAtAsc(String runId);

    long countByTokenId(UUID tokenId);
}
