package com.hms.infrastructure.persistence.hitl;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An agent run paused awaiting a human.
 *
 * <p>The transcript is stored because an operator cannot judge a conversation
 * they cannot read — but that makes this column PHI, not log data. It is
 * encrypted at rest and must never reach a log line.
 */
@Entity
@Table(name = "hitl_escalations")
@Getter
@Setter
public class HitlEscalationEntity extends AuditableEntity {

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    /**
     * Which patient this escalation concerns, so an erasure request can reach the
     * transcript.
     *
     * <p>Added in V206. Before it, {@code hitl_escalations} held free-text
     * transcripts with no patient linkage at all, which meant the erasure sweep
     * could not target them and instead matched every run in the tenant.
     * Nullable: an escalation can be raised before the caller is identified.
     */
    @Column(name = "patient_id")
    private java.util.UUID patientId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "reason", nullable = false, length = 40)
    private String reason;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "intent", length = 40)
    private String intent;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Type(JsonType.class)
    @Column(name = "proposed_actions", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> proposedActions = new ArrayList<>();

    /** WAITING | RESOLVED | TIMED_OUT | ABANDONED */
    @Column(name = "state", nullable = false, length = 20)
    private String state = "WAITING";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /** APPROVE | CORRECT | OVERRIDE | TAKE_OVER */
    @Column(name = "operator_action", length = 20)
    private String operatorAction;

    @Column(name = "operator_reason", length = 500)
    private String operatorReason;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "operator_reply", columnDefinition = "TEXT")
    private String operatorReply;

    public boolean isWaiting() {
        return "WAITING".equals(state);
    }

    public boolean isOverdue(Instant now) {
        return isWaiting() && expiresAt != null && !expiresAt.isAfter(now);
    }
}
