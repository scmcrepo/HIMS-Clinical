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
