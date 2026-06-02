package com.hms.domain.bed.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A physical bed in the hospital.
 *
 * bedStatus drives operational availability:
 *   AVAILABLE   → can be allocated to a patient
 *   ALLOCATED   → currently occupied by a patient
 *   MAINTENANCE → temporarily unavailable
 *
 * Allocation and release are managed by BedManagementService using
 * SELECT FOR UPDATE on the bed row to prevent race conditions.
 */
@Entity
@Table(name = "beds", indexes = {
    @Index(name = "idx_beds_status",   columnList = "bed_status"),
    @Index(name = "idx_beds_category", columnList = "room_category_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Bed extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "room_category_id", nullable = false)
    private UUID roomCategoryId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "bed_status", nullable = false)
    private BedStatus bedStatus = BedStatus.AVAILABLE;

    // ── Behaviour ─────────────────────────────────────────────────────────

    public boolean isAvailable()    { return bedStatus == BedStatus.AVAILABLE;    }
    public boolean isAllocated()    { return bedStatus == BedStatus.ALLOCATED;    }
    public boolean isMaintenance()  { return bedStatus == BedStatus.MAINTENANCE;  }

    /**
     * Allocate this bed.
     * Called within a PESSIMISTIC_WRITE locked transaction by BedManagementService.
     */
    public void allocate() {
        if (!isAvailable()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Bed '" + name + "' is not available (current status: " + bedStatus + ")");
        }
        this.bedStatus = BedStatus.ALLOCATED;
    }

    /** Release after patient discharge or transfer. */
    public void release() {
        if (!isAllocated()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Bed '" + name + "' is not currently allocated");
        }
        this.bedStatus = BedStatus.AVAILABLE;
    }

    public void markMaintenance() {
        if (isAllocated()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot mark an occupied bed for maintenance — discharge the patient first");
        }
        this.bedStatus = BedStatus.MAINTENANCE;
    }

    public void clearMaintenance() {
        if (!isMaintenance()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Bed is not in maintenance status");
        }
        this.bedStatus = BedStatus.AVAILABLE;
    }
}
