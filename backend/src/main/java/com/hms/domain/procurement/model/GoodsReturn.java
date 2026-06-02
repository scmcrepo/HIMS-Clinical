package com.hms.domain.procurement.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Entity @Table(name = "goods_returns") @Getter @Setter @NoArgsConstructor
public class GoodsReturn extends AuditableEntity {
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "return_date", nullable = false) private LocalDate returnDate;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;

    @OneToMany(mappedBy = "goodsReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsReturnLine> lines = new ArrayList<>();

    public void addLine(GoodsReturnLine line) {
        line.setGoodsReturn(this);
        lines.add(line);
    }
}
