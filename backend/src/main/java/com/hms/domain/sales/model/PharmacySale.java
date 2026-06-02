package com.hms.domain.sales.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Entity @Table(name = "pharmacy_sales") @Getter @Setter @NoArgsConstructor
public class PharmacySale extends AuditableEntity {
    @Column(name = "patient_id") private UUID patientId;
    @Column(name = "customer_name", length = 100) private String customerName;
    @Column(name = "customer_phone", length = 20) private String customerPhone;
    @Column(name = "consultant_name", length = 100) private String consultantName;
    @Column(name = "encounter_id") private UUID encounterId;
    @Column(name = "department_id", nullable = false) private UUID departmentId;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "sale_date", nullable = false) private LocalDate saleDate;
    @Column(name = "total_amount", precision = 14, scale = 4) private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "discount_amount", precision = 14, scale = 4) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name = "paid_amount", precision = 14, scale = 4) private BigDecimal paidAmount = BigDecimal.ZERO;
    @Column(name = "due_amount", precision = 14, scale = 4) private BigDecimal dueAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "sale_status", nullable = false) private SaleStatus saleStatus = SaleStatus.DRAFT;
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PharmacySaleLine> lines = new ArrayList<>();
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PharmacySalePayment> payments = new ArrayList<>();
    @Column(name = "payment_mode", length = 20) private String paymentMode;
    @Column(name = "card_type", length = 50) private String cardType;
    @Column(name = "card_number", length = 25) private String cardNumber;
    @Column(name = "bank_name", length = 100) private String bankName;
    // ── Behaviour ──────────────────────────────────────────────────────────
    public boolean isDraft() { return saleStatus == SaleStatus.DRAFT; }
    public void addLine(PharmacySaleLine line) { line.setSale(this); lines.add(line); recalculate(); }
    public void addPayment(PharmacySalePayment payment) { payment.setSale(this); this.payments.add(payment); }
    public void recalculate() {
        this.totalAmount = lines.stream()
            .map(PharmacySaleLine::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .subtract(this.discountAmount != null ? this.discountAmount : BigDecimal.ZERO)
            .setScale(0, java.math.RoundingMode.HALF_UP);
        if (this.saleStatus == SaleStatus.BILLED) {
            this.dueAmount = BigDecimal.ZERO;
        } else if (this.paidAmount != null) {
            this.dueAmount = this.totalAmount.subtract(this.paidAmount).max(BigDecimal.ZERO);
        } else {
            this.dueAmount = BigDecimal.ZERO;
        }
    }
    public void finalise(String seqNumber) {
        this.sequenceNumber = seqNumber;
        if ("Add to Bill".equalsIgnoreCase(this.paymentMode)) {
            this.saleStatus = SaleStatus.BILLED;
            this.dueAmount = BigDecimal.ZERO;
        } else if (this.dueAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.saleStatus = SaleStatus.WITH_DUE;
        } else {
            this.saleStatus = SaleStatus.SETTLED;
        }
        this.saleDate = LocalDate.now();
    }
}
