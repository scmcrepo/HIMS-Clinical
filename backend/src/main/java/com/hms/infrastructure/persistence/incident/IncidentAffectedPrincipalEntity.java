package com.hms.infrastructure.persistence.incident;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One person affected by an incident, and whether they have been told.
 *
 * <p>Surrogate ids only — no name, no contact detail. Contact details are read
 * from the patient record at send time rather than copied here, so this table
 * does not become a second, less protected store of the data that leaked.
 *
 * <p>{@code FAILED} is a first-class outcome. An undeliverable notification must
 * stay visible, because otherwise an incident closes with people who were never
 * reached and the register says everyone was.
 */
@Entity
@Table(name = "incident_affected_principals")
@Getter
@Setter
public class IncidentAffectedPrincipalEntity extends AuditableEntity {

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "notification_channel", length = 20)
    private String notificationChannel;

    /** PENDING | SENT | FAILED | NOT_REQUIRED */
    @Column(name = "notification_state", nullable = false, length = 20)
    private String notificationState = "PENDING";

    @Column(name = "failure_reason", length = 200)
    private String failureReason;
}
