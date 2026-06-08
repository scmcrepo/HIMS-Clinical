package com.hms.domain.inventory.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name = "inventory_batches") @Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class InventoryBatch {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "item_id", nullable = false) private UUID itemId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "batch_number", length = 50) private String batchNumber;
    @Column(name = "current_quantity", nullable = false) private int currentQuantity = 0;
    @Column(name = "free_quantity", nullable = false) private int freeQuantity = 0;
    @Column(name = "purchase_rate", nullable = false, precision = 12, scale = 4) private BigDecimal purchaseRate;
    @Column(name = "maximum_retail_price", nullable = false, precision = 12, scale = 4) private BigDecimal maximumRetailPrice;
    @Column(name = "selling_rate", nullable = false, precision = 12, scale = 4) private BigDecimal sellingRate;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Column(name = "source_transaction_id", nullable = false) private UUID sourceTransactionId;
    @CreatedBy @Column(name = "created_by", updatable = false) private UUID createdBy;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
    // ── Behaviour ──────────────────────────────────────────────────────────
    public void decrementStock(int qty) {
        if (qty > currentQuantity) throw new com.hms.exception.BusinessRuleViolationException(
            "Insufficient stock. Available: " + currentQuantity + ", Requested: " + qty);
        this.currentQuantity -= qty;
    }
    public void incrementStock(int qty) { this.currentQuantity += qty; }
    public boolean isExpired() { return expiryDate != null && LocalDate.now().isAfter(expiryDate); }
    public boolean isOutOfStock() { return currentQuantity <= 0; }
}
