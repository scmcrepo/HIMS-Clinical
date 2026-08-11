package com.hms.infrastructure.persistence.abdm;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A consent request sent to the ABDM Consent Manager — Screen 3.1. */
@Entity
@Table(name = "abdm_consent_requests")
@Getter
@Setter
public class AbdmConsentRequestEntity extends AuditableEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    /** The Consent Manager's id, absent until it answers. */
    @Column(name = "consent_request_id", length = 80)
    private String consentRequestId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    /** CAREMGT | BTG | PUBHLTH | HPAYMT | DSRCH | PATRQT */
    @Column(name = "purpose_code", nullable = false, length = 20)
    private String purposeCode;

    @Column(name = "purpose_text", columnDefinition = "TEXT")
    private String purposeText;

    /** Comma-separated ABDM health-information types. */
    @Column(name = "hi_types", nullable = false, columnDefinition = "TEXT")
    private String hiTypes;

    @Column(name = "date_range_from")
    private LocalDate dateRangeFrom;

    @Column(name = "date_range_to")
    private LocalDate dateRangeTo;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "requested_by")
    private UUID requestedBy;

    /** REQUESTED | PENDING_APPROVAL | GRANTED | DENIED | EXPIRED | REVOKED */
    @Column(name = "request_state", nullable = false, length = 24)
    private String requestState = "REQUESTED";

    @Column(name = "failure_code", length = 80)
    private String failureCode;
}
