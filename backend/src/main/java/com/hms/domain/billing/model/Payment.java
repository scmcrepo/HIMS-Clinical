package com.hms.domain.billing.model;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.*;
import java.util.UUID;
@Entity @Table(name = "payments") @Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Column(name = "patient_id") private UUID patientId;
    @Column(name = "amount", nullable = false) private long amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", length = 30) private PaymentMode paymentMode;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 30) private PaymentType paymentType;
    @Column(name = "sequence_number", length = 40) private String sequenceNumber;
    @Column(name = "payment_date", nullable = false) private LocalDate paymentDate;
    @Column(name = "status", length = 20) private String status = "Active";
    @Column(name = "notes", columnDefinition = "TEXT") private String notes;
    @CreatedBy  @Column(name = "created_by",  updatable = false) private UUID createdBy;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant recordedAt;

    // ── Helpers ──────────────────────────────────────────────────────────────
    public boolean isDeposit()       { return paymentType == PaymentType.DEPOSIT;        }
    public boolean isPayment()       { return paymentType == PaymentType.PAYMENT;        }
    public boolean isRefund()        { return paymentType == PaymentType.REFUND;         }
    public boolean isAdvanceRefund() { return paymentType == PaymentType.ADVANCE_REFUND; }
}
