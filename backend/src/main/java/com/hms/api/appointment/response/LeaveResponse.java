package com.hms.api.appointment.response;

import java.time.LocalDate;
import java.util.UUID;

public record LeaveResponse(
    UUID id,
    UUID consultantId,
    LocalDate startDate,
    LocalDate endDate,
    String reason,
    String status
) {}
