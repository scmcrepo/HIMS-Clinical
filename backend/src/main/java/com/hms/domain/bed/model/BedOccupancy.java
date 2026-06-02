package com.hms.domain.bed.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Records the duration a patient occupied a specific bed.
 * One row per allocation. Closed when the patient transfers or is discharged.
 *
 * status: 1=active, 0=closed
 */
@Entity
@Table(name = "bed_occupancies", indexes = {
    @Index(name = "idx_bo_bed",       columnList = "bed_id"),
    @Index(name = "idx_bo_encounter", columnList = "encounter_id"),
    @Index(name = "idx_bo_from",      columnList = "from_datetime")
})
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BedOccupancy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "bed_id", nullable = false)
    private UUID bedId;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "from_datetime", nullable = false)
    private Instant fromDatetime;

    @Column(name = "to_datetime")
    private Instant toDatetime;

    @Column(name = "status", nullable = false)
    private short status = 1; // 1=active, 0=closed

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "modified_by")
    private UUID modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    // ── Behaviour ─────────────────────────────────────────────────────────

    public boolean isActive() { return status == 1; }

    /**
     * Close the occupancy record when the patient leaves the bed.
     * toDatetime marks the exact moment the bed was vacated.
     */
    public void close(Instant closedAt) {
        if (!isActive()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Bed occupancy is already closed");
        }
        this.toDatetime = closedAt;
        this.status = 0;
    }
}
