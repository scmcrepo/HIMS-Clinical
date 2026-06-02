package com.hms.api.appointment.response;

import java.time.LocalTime;
import java.util.UUID;

public record SlotAvailabilityResponse(
    UUID slotId,
    LocalTime fromTime,
    LocalTime toTime,
    int maxPatients,
    int bookedCount,
    int availableCount,
    boolean isAvailable
) {}
