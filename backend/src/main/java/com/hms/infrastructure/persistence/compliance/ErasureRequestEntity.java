package com.hms.infrastructure.persistence.compliance;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "erasure_requests")
// Tenant-wide. branch_id exists on the table (V215) only because AuditableEntity
// maps it; this record belongs to the hospital, not to one of its locations, so
// branchFilter is disabled and the column stays NULL. Do not "tidy" the 1=1 away:
// re-enabling the branch filter hides compliance records from other branches.
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "1=1")
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

    /**
     * When the requester was proved to be the patient.
     *
     * <p>{@code ErasureService.sweep} refuses to run while this is null. Acting
     * on an unverified request is itself a breach: it destroys data on a
     * stranger's say-so and denies the real patient their own history.
     */
    @Column(name = "requester_verified_at")
    private Instant requesterVerifiedAt;

    /** PORTAL_OTP | IN_PERSON_ID | ABHA_VERIFIED | REGISTERED_POST | STAFF_OVERRIDE */
    @Column(name = "verification_method", length = 30)
    private String verificationMethod;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    /**
     * Statutory response deadline. Backs the overdue alert — a rights request
     * that quietly runs past its deadline is the failure mode this column exists
     * to make visible.
     */
    @Column(name = "due_at")
    private Instant dueAt;

    /** For CORRECTION requests: which fields the patient says are wrong, and what they should say. */
    @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
    @Column(name = "correction_payload", columnDefinition = "jsonb")
    private java.util.Map<String, Object> correctionPayload;

    /** Whether the patient raised this themselves, versus staff raising it for them. */
    @Column(name = "requested_by_patient", nullable = false)
    private boolean requestedByPatient = false;
}
