package com.hms.infrastructure.persistence.compliance;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "erasure_requests")
@Getter
@Setter
public class ErasureRequestEntity extends AuditableEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** ERASURE | CORRECTION */
    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType = "ERASURE";

    /** RECEIVED | IN_PROGRESS | COMPLETED | REJECTED | PARTIALLY_COMPLETED */
    @Column(name = "state", nullable = false, length = 24)
    private String state = "RECEIVED";

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "requested_via", length = 20)
    private String requestedVia;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Why some data was kept. Erasure is not absolute — clinical records carry
     * statutory retention and a claim under adjudication cannot vanish mid-flight
     * — and the patient is entitled to be told what was retained and why.
     */
    @Column(name = "retained_reason", length = 500)
    private String retainedReason;
}
