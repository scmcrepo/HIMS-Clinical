package com.hms.api.appointment.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Blocks one or more time windows across a date range.
 *
 * <p>Several windows arrive in one request rather than one request each so the
 * whole thing is a single transaction: a doctor blocking 09:00–12:00 and
 * 15:00–16:00 should end up with both or neither, never with the morning
 * blocked and the afternoon silently dropped by a failed second call. Each
 * window becomes its own {@code consultant_leaves} row, which is what makes
 * unblocking one of them an ordinary delete.
 */
public record CreateTimeBlockRequest(
    UUID consultantId,

    @NotNull(message = "Start date is required")
    LocalDate startDate,

    @NotNull(message = "End date is required")
    LocalDate endDate,

    String reason,

    @NotEmpty(message = "At least one time range is required")
    @Valid
    List<TimeRange> timeRanges
) {
    public record TimeRange(
        @NotNull(message = "Range start time is required")
        LocalTime startTime,

        @NotNull(message = "Range end time is required")
        LocalTime endTime
    ) {
        public boolean overlaps(TimeRange other) {
            return startTime.isBefore(other.endTime) && other.startTime.isBefore(endTime);
        }
    }
}
