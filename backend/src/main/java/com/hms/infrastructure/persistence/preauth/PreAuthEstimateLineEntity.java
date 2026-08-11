package com.hms.infrastructure.persistence.preauth;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a pre-auth estimate — Screen 4.1.
 *
 * <p>{@link #lineAmount} is stored rather than derived on read. When an insurer
 * disputes a figure months later, the argument is about the number that was
 * actually sent, not one recomputed under whatever rounding the code does today.
 *
 * <p>Amounts in paise; quantity is decimal because half a day of room rent and
 * 1.5 units of an implant are both real.
 */
@Entity
@Table(name = "preauth_estimate_lines")
@Getter
@Setter
public class PreAuthEstimateLineEntity extends AuditableEntity {

    @Column(name = "nhcx_transaction_id", nullable = false)
    private UUID nhcxTransactionId;

    /** ROOM | OT | IMPLANT | CONSUMABLE | INVESTIGATION | PROFESSIONAL | OTHER */
    @Column(name = "category", nullable = false, length = 24)
    private String category = "OTHER";

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_amount", nullable = false)
    private Long unitAmount;

    @Column(name = "line_amount", nullable = false)
    private Long lineAmount;

    /** Filled per line when the insurer itemises its approval. */
    @Column(name = "approved_amount")
    private Long approvedAmount;
}
