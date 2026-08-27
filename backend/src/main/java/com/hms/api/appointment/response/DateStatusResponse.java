package com.hms.api.appointment.response;

import java.time.LocalDate;

public record DateStatusResponse(
    LocalDate date,
    String status, // "AVAILABLE", "HAS_APPOINTMENTS", "FULLY_BOOKED", "LEAVE"
    int bookedCount,
    int maxCapacity
) {}
