package com.hms.api.appointment.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RescheduleAppointmentRequest(
    @NotNull LocalDate newDate,
    @NotNull LocalTime newTime,
    UUID newSlotId
) {}
