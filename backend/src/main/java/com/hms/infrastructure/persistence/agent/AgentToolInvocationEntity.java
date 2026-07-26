package com.hms.infrastructure.persistence.agent;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Append-only audit of one agent action.
 *
 * <p>Designed to answer, months later: which run, on whose behalf, from which
 * channel, with what outcome, approved by whom. Deliberately carries no patient
 * identifiers beyond a surrogate entity id and no free text — the conversation
 * transcript lives elsewhere under PHI controls, and an audit table that
 * accumulates clinical detail becomes a second patient database nobody is
 * guarding.
 *
 * <p>Never updated, never deleted by application code.
 */
@Entity
@Table(name = "agent_tool_invocations")
@Getter
@Setter
public class AgentToolInvocationEntity extends AuditableEntity {

    @Column(name = "token_id")
    private UUID tokenId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "tool_name", nullable = false, length = 80)
    private String toolName;

    /** SUCCESS | FAILURE | REPLAYED | DENIED */
    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "target_entity_type", length = 60)
    private String targetEntityType;

    @Column(name = "target_entity_id")
    private UUID targetEntityId;
}
