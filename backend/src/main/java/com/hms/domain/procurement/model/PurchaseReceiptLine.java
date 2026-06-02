package com.hms.domain.procurement.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name = "purchase_receipt_lines") @Getter @Setter @NoArgsConstructor
public class PurchaseReceiptLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false) private PurchaseReceipt receipt;
    @Column(name = "item_id", nullable = false) private UUID itemId;
    @Column(name = "batch_number", length = 50) private String batchNumber;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "purchase_rate", nullable = false, precision = 12, scale = 4) private BigDecimal purchaseRate;
    @Column(name = "maximum_retail_price", nullable = false, precision = 12, scale = 4) private BigDecimal maximumRetailPrice;
    @Column(name = "selling_rate", nullable = false, precision = 12, scale = 4) private BigDecimal sellingRate;
    @Column(name = "expiry_date") private LocalDate expiryDate;
}
