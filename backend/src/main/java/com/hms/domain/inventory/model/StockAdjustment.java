package com.hms.domain.inventory.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
@Entity @Table(name = "stock_adjustment") @Getter @Setter @NoArgsConstructor
public class StockAdjustment extends AuditableEntity {
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    @Column(name = "adjustment_date", nullable = false) private java.time.LocalDate adjustmentDate;
    @OneToMany(mappedBy = "adjustment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<StockAdjustmentLine> lines = new ArrayList<>();
    public void addLine(StockAdjustmentLine l) { l.setAdjustment(this); lines.add(l); }
}
