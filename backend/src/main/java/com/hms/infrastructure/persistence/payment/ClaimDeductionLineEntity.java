package com.hms.infrastructure.persistence.payment;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One disallowed item on a claim.
 *
 * <p>Itemised rather than a single total, because Screen 5.2's dispute path
 * needs the billing team to challenge a specific line. "₹5,000 deducted" is not
 * something anyone can argue with; "₹5,000 for non-medical consumables" is.
 */
@Entity
@Table(name = "claim_deduction_lines")
@Getter
@Setter
public class ClaimDeductionLineEntity extends AuditableEntity {

    @Column(name = "nhcx_transaction_id", nullable = false)
    private UUID nhcxTransactionId;

    /** NON_MEDICAL | NOT_COVERED | EXCEEDS_LIMIT | DOCUMENT_MISSING | TDS | OTHER */
    @Column(name = "reason_category", nullable = false, length = 24)
    private String reasonCategory = "OTHER";

    @Column(name = "reason_code", length = 60)
    private String reasonCode;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /** Paise. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "disputed", nullable = false)
    private boolean disputed = false;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "dispute_note", columnDefinition = "TEXT")
    private String disputeNote;
}
