package com.hms.infrastructure.persistence.nhcx;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One NHCX exchange, from submission to callback.
 *
 * <p>Persisted <em>before</em> the HTTP call, not after. If the process dies
 * mid-submission, a record with no acknowledgement is recoverable; a submission
 * with no record is a claim that vanished.
 *
 * <p>{@code expiresAt} is what stops a claim sitting in limbo: when the payer
 * never calls back, a sweep escalates it to a human rather than leaving the
 * hospital unpaid and unaware.
 */
@Entity
@Table(name = "nhcx_transactions")
@Getter
@Setter
public class NhcxTransactionEntity extends AuditableEntity {

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "api_call_id", length = 64)
    private String apiCallId;

    /** ELIGIBILITY | PREAUTH | CLAIM */
    @Column(name = "exchange_type", nullable = false, length = 20)
    private String exchangeType;

    @Column(name = "payer_code", nullable = false, length = 60)
    private String payerCode;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    /** The policy this exchange is against — added with Module 4. */
    @Column(name = "insurance_id")
    private UUID insuranceId;

    /** ICD-10 code. Payers reject undiagnosed pre-auths. */
    /**
     * ICD-10 code sent to the payer. Health data, and identifying in combination
     * with the patient id on the same row. WO-028.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "diagnosis_code", length = 20)
    private String diagnosisCode;

    /**
     * Free-text diagnosis. WO-028.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "diagnosis_text", columnDefinition = "TEXT")
    private String diagnosisText;

    @Column(name = "planned_procedure", columnDefinition = "TEXT")
    private String plannedProcedure;

    @Column(name = "expected_los_days")
    private Integer expectedLosDays;

    @Column(name = "room_type", length = 120)
    private String roomType;

    /** Paise. The estimate total, computed from the lines. */
    @Column(name = "estimated_amount")
    private Long estimatedAmount;

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "claim_amount")
    private Long claimAmount;

    /** SUBMITTED | ACKNOWLEDGED | APPROVED | REJECTED | TIMED_OUT | FAILED */
    @Column(name = "state", nullable = false, length = 20)
    private String state = "SUBMITTED";

    @Column(name = "outcome_code", length = 60)
    private String outcomeCode;

    @Column(name = "approved_amount")
    private Long approvedAmount;

    /**
     * The money lifecycle, separate from {@code state}.
     *
     * <p>{@code state} tracks the NHCX exchange; this tracks whether the
     * hospital has been paid. A claim can be exchange-complete and financially
     * unpaid for weeks, and one column cannot express both without hiding that.
     *
     * <p>CLAIM_SUBMITTED | CLAIM_APPROVED | PAYMENT_INITIATED |
     * AMOUNT_RECEIVED_IN_BANK | CLAIM_DISPUTED
     */
    @Column(name = "financial_state", length = 28)
    private String financialState;

    /** Paise. What the hospital asked for. */
    @Column(name = "claimed_amount")
    private Long claimedAmount;

    /** Paise. Claimed minus approved, itemised in claim_deduction_lines. */
    @Column(name = "disallowed_amount")
    private Long disallowedAmount;

    /** Paise. The patient's co-pay share of the approved amount. */
    @Column(name = "patient_copay_amount")
    private Long patientCopayAmount;

    /** The payer's response bundle contains clinical detail — encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isAwaitingCallback() {
        return "SUBMITTED".equals(state) || "ACKNOWLEDGED".equals(state);
    }
}
