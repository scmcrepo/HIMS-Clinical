package com.hms.infrastructure.persistence.payment;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * An insurer's payment advice — the NHCX PaymentNotice of Screen 5.3.
 *
 * <p>Two distinct facts live here and are deliberately not merged. The payer's
 * assertion ({@link #netDisbursedAmount}, {@link #utrNumber}) is what the
 * insurer says it sent. The reconciliation fields
 * ({@link #bankCreditedAmount}, {@link #reconciled}) are what the hospital's
 * accounts team confirmed actually landed. Comparing the two is the entire
 * purpose of the screen, so storing one value for both would destroy it.
 *
 * <p>All amounts in paise.
 */
@Entity
@Table(name = "claim_payment_advices")
@Getter
@Setter
public class ClaimPaymentAdviceEntity extends AuditableEntity {

    @Column(name = "nhcx_transaction_id", nullable = false)
    private UUID nhcxTransactionId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "payer_code", nullable = false, length = 60)
    private String payerCode;

    /**
     * Bank UTR / NEFT reference. Unique per tenant — the same UTR arriving twice
     * is a duplicate advice, not a second payment.
     */
    @Column(name = "utr_number", nullable = false, length = 64)
    private String utrNumber;

    @Column(name = "payment_date")
    private Instant paymentDate;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "tds_amount", nullable = false)
    private Long tdsAmount = 0L;

    @Column(name = "deduction_amount", nullable = false)
    private Long deductionAmount = 0L;

    @Column(name = "net_disbursed_amount", nullable = false)
    private Long netDisbursedAmount;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled = false;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    @Column(name = "reconciled_by")
    private UUID reconciledBy;

    /** What the bank statement actually showed. Set by accounts, not the payer. */
    @Column(name = "bank_credited_amount")
    private Long bankCreditedAmount;

    @Column(name = "reconciliation_note", columnDefinition = "TEXT")
    private String reconciliationNote;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;
}
