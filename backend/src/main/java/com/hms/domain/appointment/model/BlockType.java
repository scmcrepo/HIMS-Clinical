package com.hms.domain.appointment.model;

/**
 * How much of a day a {@link ConsultantLeave} row takes out of circulation.
 *
 * <p>{@code FULL_DAY} is the original behaviour and the default for every row
 * written before partial blocking existed: the consultant is unavailable for
 * the whole of each date in the range.
 *
 * <p>{@code TIME_RANGE} blocks only {@code startTime}..{@code endTime} on each
 * date in the range, leaving the rest of the day bookable. Several
 * non-contiguous windows on one day are several rows.
 */
public enum BlockType {
    FULL_DAY,
    TIME_RANGE
}
