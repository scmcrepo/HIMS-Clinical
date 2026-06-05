package com.hms.api.appointment.response;

import com.hms.domain.appointment.model.AppointmentStatus;
import com.hms.domain.encounter.model.VisitMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,
    UUID patientId,
    String patientNumber,
    String patientName,
    UUID providerId,
    String providerName,
    UUID slotId,
    AppointmentStatus status,
    LocalDate appointmentDate,
    LocalTime appointmentTime,
    VisitMode visitMode,
    String notes,
    String tempPatientName,
    String tempPatientSalutation,
    String tempPatientGender,
    String tempPatientPhone,
    Integer tempPatientAge,
    String patientPhone,
    LocalTime appointmentEndTime,
    int bookedCount,
    int maxPatients
) {}
