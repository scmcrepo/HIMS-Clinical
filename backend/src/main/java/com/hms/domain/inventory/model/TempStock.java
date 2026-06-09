package com.hms.domain.inventory.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name = "temp_stock") @Getter @Setter @NoArgsConstructor
public class TempStock {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "item_id", nullable = false) private UUID itemId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "batch_number", length = 50) private String batchNumber;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "purchase_rate", nullable = false, precision = 12, scale = 4) private BigDecimal purchaseRate;
    @Column(name = "mrp", nullable = false, precision = 12, scale = 4) private BigDecimal mrp;
    @Column(name = "selling_rate", nullable = false, precision = 12, scale = 4) private BigDecimal sellingRate;
    @Column(name = "expiry_date") private LocalDate expiryDate;
    @Column(name = "source_receipt_id") private UUID sourceReceiptId;
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2) private BigDecimal taxRate = BigDecimal.ZERO;
    @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt = Instant.now();
}
