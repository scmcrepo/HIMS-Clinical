package com.hms.domain.procurement.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name = "purchase_orders_lines") @Getter @Setter @NoArgsConstructor
public class PurchaseOrderLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false) private PurchaseOrder order;
    @Column(name = "item_id", nullable = false) private UUID itemId;
    @Column(name = "quantity", nullable = false) private int quantity;
    @Column(name = "received_quantity", nullable = false) private int receivedQuantity = 0;
    @Column(name = "unit_rate", precision = 12, scale = 4) private BigDecimal unitRate;
}
