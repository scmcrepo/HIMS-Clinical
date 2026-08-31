package com.hms.domain.procurement.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tenant scope is carried by the goods return through its foreign key. Adding
 * a redundant tenant_id here would create a second source of truth for the
 * same fact, and a new way for the two to disagree. Not platform-level;
 * deliberately parent-scoped.
 */
@Entity
@Table(name = "goods_return_lines")
@Getter
@Setter
@NoArgsConstructor
public class GoodsReturnLine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private GoodsReturn goodsReturn;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "free_quantity", nullable = false)
    private int freeQuantity;

    @Column(name = "purchase_rate", nullable = false, precision = 12, scale = 4)
    private BigDecimal purchaseRate;
}
