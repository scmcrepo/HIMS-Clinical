package com.hms.domain.appointment.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * A time slot for a consultant on a specific day of the week.
 *
 * One row per consultant+day+time combination.
 * dayOfWeek ordinal: MON=0, TUE=1, WED=2, THU=3, FRI=4, SAT=5, SUN=6.
 * concatTime = fromTime.toString() + toTime.toString() (string concat, legacy join key).
 *
 * Soft-delete: if appointments exist for a slot, status=INACTIVE instead of physical delete.
 */
@Entity
@Table(name = "appointment_slots", indexes = {
    @Index(name = "idx_slot_consultant", columnList = "consultant_id"),
    @Index(name = "idx_slot_day",        columnList = "consultant_id, day_of_week")
})
@Getter @Setter @NoArgsConstructor
public class AppointmentSlot extends AuditableEntity {

    @Column(name = "consultant_id", nullable = false)
    private UUID consultantId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeekEnum dayOfWeek;

    @Column(name = "from_time", nullable = false, length = 30)
    private String fromTime;

    @Column(name = "to_time", nullable = false, length = 30)
    private String toTime;

    @Column(name = "concat_time", nullable = false, length = 60)
    private String concatTime;

    @Column(name = "number_of_patients", nullable = false)
    private int maxPatients = 10;

    public void buildConcatTime() {
        this.concatTime = fromTime + toTime;
    }

    public boolean overlaps(AppointmentSlot other) {
        if (!this.dayOfWeek.equals(other.dayOfWeek)) return false;
        // String comparison works for HH:mm format
        return this.fromTime.compareTo(other.toTime) < 0 && other.fromTime.compareTo(this.toTime) < 0;
    }
}
