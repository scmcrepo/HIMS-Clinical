package com.hms.domain.procurement.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Entity @Table(name = "purchase_orders") @Getter @Setter @NoArgsConstructor
public class PurchaseOrder extends AuditableEntity {
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "order_date", nullable = false) private LocalDate orderDate = LocalDate.now();
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "order_status", length = 20) private String orderStatus = "ORDERED";
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PurchaseOrderLine> lines = new ArrayList<>();
    public void addLine(PurchaseOrderLine l) { l.setOrder(this); lines.add(l); }
}
