package com.hms.api.agent.v1.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Booking request from an agent.
 *
 * <p>Note what is absent: no walk-in name/phone fields. The human-facing
 * {@code BookAppointmentRequest} accepts {@code tempPatientName} and
 * {@code tempPatientPhone} for counter registrations, and those are unencrypted
 * on that path. Exposing them here would widen an existing PII gap to a
 * machine-driven, high-volume caller. An agent books for an already-registered
 * patient or it does not book.
 */
public record BookSlotToolRequest(
    @NotNull UUID providerId,
    @NotNull UUID slotId,
    @NotNull LocalDate appointmentDate,
    @NotNull UUID patientId,
    String notes
) {
}
