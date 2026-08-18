package com.hms.domain.insurance.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One cheque or electronic remittance received from a TPA against a claim
 * (WO-020, Stage 7).
 *
 * <p>A real table rather than the source system's {@code cheque_list} JSON
 * column (decision D-1). These rows carry money the ageing and settlement
 * reports must {@code SUM}, they need their own {@code created_by} so a
 * mis-keyed cheque is attributable to the clerk who keyed it, and a JSONB blob
 * can enforce neither a positive amount nor a tenant boundary.
 *
 * <p>A claim is routinely settled in several tranches — an interim payment on
 * dispatch and a balance after the disallowance dispute — so this is a
 * many-to-one, not a field on the claim.
 *
 * <p>Amount is in <b>paise</b> and must be positive. A negative receipt would be
 * a refund back to the insurer, which is a different transaction with different
 * approval rules and does not belong here.
 */
@Entity
@Table(name = "insurance_cheque_receipts", indexes = {
    @Index(name = "ix_icr_insurance", columnList = "insurance_id")
})
@Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class InsuranceChequeReceipt extends AuditableEntity {

    @Column(name = "insurance_id", nullable = false)
    private UUID insuranceId;

    /**
     * Cheque number or NEFT/RTGS UTR.
     *
     * <p>Not encrypted: it is the insurer's banking reference, and finance
     * reconciles it against a bank statement by eye. It is still kept out of
     * log statements — a payment reference tied to a named patient's claim is
     * exactly the pairing that makes a log line sensitive.
     */
    @Column(name = "cheque_no", nullable = false, length = 100)
    private String chequeNo;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    /** Issuing bank. */
    @Column(name = "drawn_on", length = 150)
    private String drawnOn;

    /** Issuing branch. */
    @Column(name = "payable_at", length = 150)
    private String payableAt;

    /** Net amount disbursed, in paise. Always positive. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /** TPA officer or signatory who authorised the payment. */
    @Column(name = "authorised_by", length = 150)
    private String authorisedBy;
}
