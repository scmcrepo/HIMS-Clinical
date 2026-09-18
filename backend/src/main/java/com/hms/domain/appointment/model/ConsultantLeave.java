package com.hms.domain.appointment.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Stores leave/unavailability ranges for a consultant (doctor).
 * During these dates, new appointments cannot be booked.
 *
 * <p>A {@link BlockType#FULL_DAY} row takes out every date from
 * {@code startDate} to {@code endDate}. A {@link BlockType#TIME_RANGE} row
 * takes out only {@code startTime}..{@code endTime} on each of those dates,
 * so the same doctor can be in theatre every morning of a week and still see
 * patients each afternoon.
 */
@Entity
@Table(name = "consultant_leaves", indexes = {
    @Index(name = "idx_leave_consultant", columnList = "consultant_id"),
    @Index(name = "idx_leave_range", columnList = "consultant_id, start_date, end_date"),
    @Index(name = "idx_leave_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "(branch_id IS NULL OR branch_id = :branchId)")
public class ConsultantLeave extends AuditableEntity {

    @Column(name = "consultant_id", nullable = false)
    private UUID consultantId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, length = 20)
    private BlockType blockType = BlockType.FULL_DAY;

    /** Inclusive start of the blocked window; {@code null} for a full-day leave. */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** Exclusive end of the blocked window; {@code null} for a full-day leave. */
    @Column(name = "end_time")
    private LocalTime endTime;

    public boolean isFullDay() {
        return blockType == null || blockType == BlockType.FULL_DAY;
    }

    /**
     * Whether this row blocks the window {@code [from, to)}.
     *
     * <p>Ends are exclusive on both sides, so a 09:00–12:00 block leaves a
     * 12:00–13:00 slot bookable. A full-day row swallows any window.
     */
    public boolean blocks(LocalTime from, LocalTime to) {
        if (isFullDay()) return true;
        if (startTime == null || endTime == null || from == null || to == null) return false;
        return from.isBefore(endTime) && startTime.isBefore(to);
    }

    /** Whether this row's window overlaps {@code other}'s on a shared date. */
    public boolean overlapsWindowOf(ConsultantLeave other) {
        if (isFullDay() || other.isFullDay()) return true;
        return blocks(other.getStartTime(), other.getEndTime());
    }

    /** Whether the date range of this row and {@code other} intersect at all. */
    public boolean overlapsDatesOf(LocalDate from, LocalDate to) {
        return !endDate.isBefore(from) && !startDate.isAfter(to);
    }
}
