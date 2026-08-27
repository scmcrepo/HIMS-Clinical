package com.hms.api.appointment.response;

import java.util.List;

/**
 * Wraps slot availability with metadata explaining WHY slots may be empty.
 * Used by the /availability-check endpoint so the frontend can display
 * contextual warnings (e.g., "Doctor is on leave").
 */
public record AvailabilityCheckResponse(
    List<SlotAvailabilityResponse> slots,
    String reason,        // null = normal, "ON_LEAVE", "NO_SLOTS"
    String dayOfWeek      // "MONDAY", "TUESDAY", etc.
) {}
