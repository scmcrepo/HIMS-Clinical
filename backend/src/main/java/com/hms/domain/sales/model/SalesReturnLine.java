package com.hms.domain.sales.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "sales_return_lines") @Getter @Setter @NoArgsConstructor
/**
 * Tenant scope is carried by the sales return through its foreign key. Adding
 * a redundant tenant_id here would create a second source of truth for the
 * same fact, and a new way for the two to disagree. Not platform-level;
 * deliberately parent-scoped.
 */
public class SalesReturnLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;

    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_return_id", nullable = false) private SalesReturn salesReturn;

    @Column(name = "inventory_batch_id", nullable = false) private UUID inventoryBatchId;
    @Column(name = "sale_line_id") private UUID saleLineId;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "return_amount", nullable = false, precision = 14, scale = 4) private BigDecimal returnAmount;
}
