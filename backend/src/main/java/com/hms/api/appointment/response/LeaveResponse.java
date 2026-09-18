package com.hms.api.appointment.response;

import com.hms.domain.appointment.model.ConsultantLeave;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record LeaveResponse(
    UUID id,
    UUID consultantId,
    LocalDate startDate,
    LocalDate endDate,
    String reason,
    String status,
    /** FULL_DAY | TIME_RANGE */
    String blockType,
    /** Inclusive start of the blocked window; null for a full-day leave. */
    LocalTime startTime,
    /** Exclusive end of the blocked window; null for a full-day leave. */
    LocalTime endTime
) {
    /**
     * Built in one place because six call sites were each spelling out the same
     * six-argument constructor, and the three new block fields would have made
     * that six near-identical nine-argument literals.
     */
    public static LeaveResponse from(ConsultantLeave leave) {
        return new LeaveResponse(
            leave.getId(),
            leave.getConsultantId(),
            leave.getStartDate(),
            leave.getEndDate(),
            leave.getReason(),
            leave.getStatus().name(),
            leave.isFullDay() ? "FULL_DAY" : leave.getBlockType().name(),
            leave.getStartTime(),
            leave.getEndTime());
    }
}
