package com.hms.infrastructure.persistence.grievance;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * One step taken on a grievance.
 *
 * <p>A separate table rather than columns on the grievance, because "who did
 * what and when" is the evidence that the mechanism is effective rather than
 * merely present. A state field alone cannot show anyone actually worked on it.
 *
 * <p>{@link #communicated} distinguishes doing something from telling the
 * complainant you did — which is the difference they actually notice, and the
 * one a state machine tends to lose.
 */
@Entity
@Table(name = "grievance_events")
// Tenant-wide. branch_id exists on the table (V215) only because AuditableEntity
// maps it; this record belongs to the hospital, not to one of its locations, so
// branchFilter is disabled and the column stays NULL. Do not "tidy" the 1=1 away:
// re-enabling the branch filter hides compliance records from other branches.
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "1=1")
@Getter
@Setter
public class GrievanceEventEntity extends AuditableEntity {

    @Column(name = "grievance_id", nullable = false)
    private UUID grievanceId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    /** Internal working note. Encrypted — staff write freely and it will contain patient detail. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "communicated", nullable = false)
    private boolean communicated;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
