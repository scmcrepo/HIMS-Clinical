package com.hms.domain.billing.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Bill aggregate root.
 *
 * Money fields are stored as BIGINT in the smallest currency unit (paise / cents).
 * dueAmount is NEVER persisted — always derived by computeDueAmount().
 *
 * Status transitions are managed exclusively through BillingEngine.recomputeStatus()
 * and must never be set directly from the API layer.
 */
@Entity
@Table(name = "bills")
@Getter
@Setter
@NoArgsConstructor
public class Bill extends AuditableEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "primary_provider_id")
    private UUID primaryProviderId;

    @Column(name = "payor_id")
    private UUID payorId;

    @Column(name = "referral_id")
    private UUID referralId;

    @Column(name = "bill_amount", nullable = false)
    private long billAmount = 0L;

    @Column(name = "discount_total", nullable = false)
    private long discountTotal = 0L;

    @Column(name = "payment_total", nullable = false)
    private long paymentTotal = 0L;

    @Column(name = "service_refund_total", nullable = false)
    private long serviceRefundTotal = 0L;

    @Column(name = "discount_refund_total", nullable = false)
    private long discountRefundTotal = 0L;

    @Column(name = "refund_total", nullable = false)
    private long refundTotal = 0L;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "bill_status", nullable = false)
    private BillStatus billStatus = BillStatus.DRAFT;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "bill_type", nullable = false)
    private BillType billType;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "encounter_type", nullable = false, updatable = false)
    private EncounterType encounterType;

    @Column(name = "bill_date")
    private LocalDate billDate;

    @Column(name = "admission_at")
    private Instant admissionAt;

    @Column(name = "discharge_at")
    private Instant dischargeAt;

    @Column(name = "bed_number")
    private String bedNumber;

    /** Formatted bill number — e.g. BILL-00123. Generated on generateBill(). */
    @Column(name = "bill_number", length = 40)
    private String billNumber;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<ChargeLineItem> chargeLineItems = new ArrayList<>();

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("recordedAt ASC")
    private List<Payment> payments = new ArrayList<>();

    // ── Derived / computed behaviour ─────────────────────────────────────────

    /**
     * Core HMS formula — never stored in DB.
     * dueAmount = billAmount - discountTotal - paymentTotal - serviceRefundTotal + refundTotal
     */
    public long computeDueAmount() {
        return billAmount - discountTotal - paymentTotal - serviceRefundTotal + refundTotal;
    }

    public boolean isDraft()     { return billStatus == BillStatus.DRAFT;     }
    public boolean isGenerated() { return billStatus != BillStatus.DRAFT;     }
    public boolean isCancelled() { return billStatus == BillStatus.CANCELLED; }

    public boolean isInpatient()  { return encounterType == EncounterType.INPATIENT;  }
    public boolean isOutpatient() { return encounterType == EncounterType.OUTPATIENT; }

    // ── Package-private mutation helpers (used only by BillingEngine) ────────

    public void addToBillAmount(long amount)          { this.billAmount          += amount; }
    public void addToPaymentTotal(long amount)        { this.paymentTotal        += amount; }
    public void addToRefundTotal(long amount)         { this.refundTotal         += amount; }
    public void addToServiceRefundTotal(long amount)  { this.serviceRefundTotal  += amount; }
    public void addToDiscountRefundTotal(long amount) { this.discountRefundTotal += amount; }
    public void setDiscountTotal(long total)          { this.discountTotal        = total;  }
    public void subtractFromBillAmount(long amount)   { this.billAmount          -= amount; }
}
