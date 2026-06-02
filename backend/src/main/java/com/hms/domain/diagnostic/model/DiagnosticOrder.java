package com.hms.domain.diagnostic.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A diagnostic order placed during a clinical encounter.
 * One order contains many DiagnosticOrderLines (one per test/study).
 *
 * Type: LAB (0) or RADIOLOGY (1).
 * Lifecycle is split into PaymentStatus and TestStatus.
 */
@Entity
@Table(name = "diagnostic_orders", indexes = {
    @Index(name = "idx_do_encounter",  columnList = "encounter_id"),
    @Index(name = "idx_do_order_date", columnList = "order_date")
})
@Getter @Setter @NoArgsConstructor
public class DiagnosticOrder extends AuditableEntity {

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;
    
    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", insertable = false, updatable = false)
    private com.hms.domain.patient.model.Patient patient;

    @Column(name = "provider_id")
    private UUID providerId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "diagnostic_type", nullable = false, updatable = false)
    private DiagnosticType diagnosticType;

    @Column(name = "sequence_number", length = 40)
    private String sequenceNumber;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "payment_status", nullable = false)
    private DiagnosticPaymentStatus paymentStatus = DiagnosticPaymentStatus.ORDERED;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "test_status", nullable = false)
    private DiagnosticTestStatus testStatus = DiagnosticTestStatus.PENDING;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DiagnosticOrderLine> lines = new ArrayList<>();

    // ── Behaviour ─────────────────────────────────────────────────────────

    public boolean isLab()       { return diagnosticType == DiagnosticType.LAB;       }
    public boolean isRadiology() { return diagnosticType == DiagnosticType.RADIOLOGY; }
    public boolean isCancelled() { return testStatus == DiagnosticTestStatus.CANCELLED;  }
    public boolean isBilled()    { return paymentStatus == DiagnosticPaymentStatus.BILLED;     }

    public void cancel() {
        if (testStatus == DiagnosticTestStatus.RESULTED) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot cancel a diagnostic order that already has results");
        }
        this.testStatus = DiagnosticTestStatus.CANCELLED;
        this.lines.forEach(DiagnosticOrderLine::cancel);
    }

    public void markBilled() {
        if (paymentStatus == DiagnosticPaymentStatus.BILLED) return;
        this.paymentStatus = DiagnosticPaymentStatus.BILLED;
    }

    public void markPartPaid() {
        if (paymentStatus == DiagnosticPaymentStatus.BILLED) return;
        this.paymentStatus = DiagnosticPaymentStatus.PART_PAID;
    }

    public void markResulted() {
        if (isCancelled()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot record results on a cancelled order");
        }
        this.testStatus = DiagnosticTestStatus.RESULTED;
    }
}
