package com.hms.domain.sales.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Pharmacy sales return.
 *
 * Validation: totalReturnQty (existing + new) must NOT exceed original sale quantity.
 * Stock restoration: stockService.returnSaleStock() called for each returned line.
 * If bill == null: creates REFUND Collection for the return amount.
 *
 * NOTE: Uses SALES prefix (same sequence as PharmacySale) — replicates legacy bug.
 */
@Entity
@Table(name = "sales_returns")
@Getter @Setter @NoArgsConstructor
public class SalesReturn extends AuditableEntity {

    @Column(name = "sale_id", nullable = false)
    private UUID saleId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "sequence_number", length = 40)
    private String sequenceNumber;

    @Column(name = "total_return_amount", precision = 14, scale = 4)
    private BigDecimal totalReturnAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "salesReturn", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SalesReturnLine> lines = new ArrayList<>();

    public void addLine(SalesReturnLine line) {
        line.setSalesReturn(this);
        lines.add(line);
        totalReturnAmount = totalReturnAmount.add(line.getReturnAmount());
    }
}
