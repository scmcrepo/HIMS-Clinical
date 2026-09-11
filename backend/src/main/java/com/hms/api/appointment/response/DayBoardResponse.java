package com.hms.api.appointment.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * One day's clinic, as a board: who is sitting, in which sessions, and how full each is.
 *
 * <p>Exists because the question a receptionist actually asks is "who can see this patient
 * today?", and answering it from the per-consultant availability endpoint means one request
 * per doctor. This is the same information for the whole clinic in a single call.
 *
 * <p>A doctor appears here only if they have sessions configured for the date. Someone on
 * leave still appears — with {@code onLeave} set and their sessions listed — because "Dr.
 * Sharma is away today" is a useful answer, whereas silently omitting them looks
 * indistinguishable from a doctor who never sits on Thursdays.
 */
public record DayBoardResponse(
    LocalDate date,
    String dayOfWeek,
    List<Doctor> doctors
) {

    public record Doctor(
        UUID consultantId,
        String name,
        String speciality,
        boolean onLeave,
        String leaveReason,
        List<Session> sessions
    ) {}

    public record Session(
        UUID slotId,
        LocalTime fromTime,
        LocalTime toTime,
        int maxPatients,
        int bookedCount,
        int availableCount
    ) {}
}
