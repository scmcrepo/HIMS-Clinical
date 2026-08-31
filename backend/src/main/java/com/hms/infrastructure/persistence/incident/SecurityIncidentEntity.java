package com.hms.infrastructure.persistence.incident;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One personal data breach or near-miss.
 *
 * <p>This is the record an inquiry asks for first: what happened, when we knew,
 * who was affected, and when each party was told. It is deliberately separate
 * from application logs — logs rotate, and the one thing that must survive a
 * retention sweep is the account of the breach.
 *
 * <p>{@link #detail} must never contain personal data. Who was affected lives in
 * {@code incident_affected_principals} as surrogate ids; a breach register that
 * accumulates personal data enlarges the problem it exists to manage.
 */
@Entity
@Table(name = "security_incidents")
@Getter
@Setter
public class SecurityIncidentEntity extends AuditableEntity {

    @Column(name = "incident_ref", nullable = false, length = 30)
    private String incidentRef;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "severity", nullable = false, length = 10)
    private String severity;

    /**
     * When we became aware. Both Rule 7 clocks run from here — an incident
     * discovered Monday and filed Tuesday is still a Monday incident.
     */
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** When it actually happened, if that is knowable. Often it is not. */
    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "detection_source", nullable = false, length = 40)
    private String detectionSource;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "data_categories", length = 300)
    private String dataCategories;

    @Column(name = "affected_principal_count", nullable = false)
    private int affectedPrincipalCount;

    /**
     * True when the blast radius could not be established.
     *
     * <p>An unknown scope is itself a finding. Recording it as zero would make
     * an unbounded breach look like a contained one, which is the most damaging
     * thing this register could get wrong.
     */
    @Column(name = "scope_uncertain", nullable = false)
    private boolean scopeUncertain;

    /** OPEN | CONTAINED | NOTIFIED | CLOSED | DISMISSED */
    @Column(name = "state", nullable = false, length = 20)
    private String state = "OPEN";

    @Column(name = "contained_at")
    private Instant containedAt;

    @Column(name = "board_notified_at")
    private Instant boardNotifiedAt;

    @Column(name = "board_detail_report_at")
    private Instant boardDetailReportAt;

    @Column(name = "board_reference", length = 80)
    private String boardReference;

    @Column(name = "principals_notified_at")
    private Instant principalsNotifiedAt;

    @Column(name = "remediation", columnDefinition = "TEXT")
    private String remediation;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;
}
