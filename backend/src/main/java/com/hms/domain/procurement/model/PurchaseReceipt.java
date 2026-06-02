package com.hms.domain.procurement.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Entity @Table(name = "purchase_receipts") @Getter @Setter @NoArgsConstructor
public class PurchaseReceipt extends AuditableEntity {
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(name = "purchase_order_id") private UUID purchaseOrderId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "receipt_date", nullable = false) private LocalDate receiptDate;
    @Column(name = "invoice_number", length = 60) private String invoiceNumber;
    @Column(name = "invoice_date") private LocalDate invoiceDate;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "receipt_status", nullable = false) private short receiptStatus = 0;
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PurchaseReceiptLine> lines = new ArrayList<>();
    public void addLine(PurchaseReceiptLine line) { line.setReceipt(this); lines.add(line); }
}
