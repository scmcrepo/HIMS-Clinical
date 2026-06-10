package com.hms.api.appointment.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

public record BookAppointmentRequest(
    UUID patientId,
    @NotNull UUID providerId,
    @NotNull UUID slotId,
    @NotNull LocalDate appointmentDate,
    String notes,
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Patient name must contain only alphabets") String tempPatientName,
    String tempPatientSalutation,
    String tempPatientGender,
    String tempPatientPhone,
    Integer tempPatientAge
) {}
