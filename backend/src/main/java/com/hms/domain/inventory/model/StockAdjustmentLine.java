package com.hms.domain.inventory.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name = "stock_adjustment_lines") @Getter @Setter @NoArgsConstructor
public class StockAdjustmentLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_id", nullable = false) private StockAdjustment adjustment;
    @Column(name = "inventory_batch_id", nullable = false) private UUID inventoryBatchId;
    @Column(name = "adjustment_qty", nullable = false) private int adjustmentQty;
    @Column(name = "adjustment_type", nullable = false, length = 10) private String adjustmentType; // ADD or SUBTRACT
    @Column(name = "reason", length = 255) private String reason;
}
