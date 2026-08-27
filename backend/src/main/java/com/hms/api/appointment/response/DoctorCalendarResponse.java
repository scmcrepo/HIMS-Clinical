package com.hms.api.appointment.response;

import java.util.List;

public record DoctorCalendarResponse(
    List<AppointmentResponse> appointments,
    List<LeaveResponse> leaves,
    List<DateStatusResponse> dateStatuses
) {}
