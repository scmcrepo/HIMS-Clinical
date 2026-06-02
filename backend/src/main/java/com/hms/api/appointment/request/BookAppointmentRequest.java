package com.hms.api.appointment.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record BookAppointmentRequest(
    UUID patientId,
    @NotNull UUID providerId,
    @NotNull UUID slotId,
    @NotNull LocalDate appointmentDate,
    String notes,
    String tempPatientName,
    String tempPatientSalutation,
    String tempPatientGender,
    String tempPatientPhone
) {}
