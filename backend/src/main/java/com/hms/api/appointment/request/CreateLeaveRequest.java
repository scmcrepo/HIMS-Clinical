package com.hms.api.appointment.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateLeaveRequest(
    @NotNull(message = "Start date is required")
    LocalDate startDate,
    
    @NotNull(message = "End date is required")
    LocalDate endDate,
    
    String reason,
    
    java.util.UUID consultantId
) {}
