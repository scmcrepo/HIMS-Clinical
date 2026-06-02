package com.hms.api.appointment.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalTime;
import java.util.UUID;

public record CreateSlotRequest(
    @NotNull UUID providerId,
    @NotNull @Min(0) @Max(6) Short dayOfWeek,
    @NotNull LocalTime fromTime,
    @NotNull LocalTime toTime,
    @Positive int maxPatients
) {}
