package com.hms.domain.billing.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
/** Audit record for charge line edits — mirrors legacy BillDetailModified. */
@Entity @Table(name = "bill_detail_modified") @Getter @Setter @NoArgsConstructor
public class BillDetailModified {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "charge_line_item_id", nullable = false) private UUID chargeLineItemId;
    @Column(name = "previous_amount",   nullable = false) private long previousAmount;
    @Column(name = "previous_rate",     nullable = false) private long previousRate;
    @Column(name = "previous_quantity", nullable = false) private int  previousQuantity;
    @Column(name = "reason", length = 500) private String reason;
    @Column(name = "modified_by") private UUID modifiedBy;
    @Column(name = "modified_at", nullable = false) private Instant modifiedAt = Instant.now();
}
