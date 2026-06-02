package com.hms.api.patient.response;
import com.hms.domain.patient.model.Gender;
import com.hms.domain.shared.model.EntityStatus;
import java.time.LocalDate;
import java.util.UUID;
public record PatientResponse(
    UUID id, String patientNumber, String salutation, String firstName, String lastName,
    String fullName, Gender gender,
    LocalDate dateOfBirth, LocalDate estimatedDateOfBirth,
    String age, String contactNumber, String email, String bloodGroup, String address,
    UUID primaryProviderId, UUID areaId, UUID categoryId,
    boolean isClinicalTrial, EntityStatus status,
    boolean isInpatient, UUID activeEncounterId
) {}
