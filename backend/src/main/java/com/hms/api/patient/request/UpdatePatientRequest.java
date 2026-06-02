package com.hms.api.patient.request;
import com.hms.domain.patient.model.Gender;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePatientRequest(
    String salutation, String firstName, String lastName, Gender gender,
    @PastOrPresent LocalDate dateOfBirth, @PastOrPresent LocalDate estimatedDateOfBirth,
    String contactNumber, String email, String bloodGroup, String address,
    UUID primaryProviderId, UUID areaId, boolean isClinicalTrial
) {}
