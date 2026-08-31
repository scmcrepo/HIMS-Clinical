package com.hms.domain.sales.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "pharmacy_sale_lines") @Getter @Setter @NoArgsConstructor
/**
 * Tenant scope is carried by the pharmacy sale through its foreign key.
 * Adding a redundant tenant_id here would create a second source of truth for
 * the same fact, and a new way for the two to disagree. Not platform-level;
 * deliberately parent-scoped.
 */
@EntityListeners(AuditingEntityListener.class)
public class PharmacySaleLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false) private PharmacySale sale;
    @Column(name = "inventory_batch_id", nullable = false) private UUID inventoryBatchId;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "unit_rate", nullable = false, precision = 12, scale = 4) private BigDecimal unitRate;
    @Column(name = "amount", nullable = false, precision = 14, scale = 4) private BigDecimal amount;
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 4) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "discount_type", length = 20) private String discountType = "AMOUNT";
    @Column(name = "discount_value", precision = 14, scale = 4) private BigDecimal discountValue = BigDecimal.ZERO;
    @CreatedBy  @Column(name = "created_by",  updatable = false) private UUID createdBy;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;
}
