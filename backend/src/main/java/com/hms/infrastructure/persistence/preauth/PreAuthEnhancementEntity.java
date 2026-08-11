package com.hms.infrastructure.persistence.preauth;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A request for more cover than was originally approved — Screen 4.4.
 *
 * <p>{@link #previousApproved} is snapshotted so the delta stays reconstructable
 * after the headline approved amount moves. Without it, a second enhancement
 * cannot tell what the first one achieved.
 */
@Entity
@Table(name = "preauth_enhancements")
@Getter
@Setter
public class PreAuthEnhancementEntity extends AuditableEntity {

    @Column(name = "nhcx_transaction_id", nullable = false)
    private UUID nhcxTransactionId;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber = 1;

    @Column(name = "previous_approved", nullable = false)
    private Long previousApproved;

    @Column(name = "revised_estimate", nullable = false)
    private Long revisedEstimate;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /** SUBMITTED | APPROVED | REJECTED | QUERY_RAISED */
    @Column(name = "enhancement_state", nullable = false, length = 24)
    private String enhancementState = "SUBMITTED";

    @Column(name = "approved_amount")
    private Long approvedAmount;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
