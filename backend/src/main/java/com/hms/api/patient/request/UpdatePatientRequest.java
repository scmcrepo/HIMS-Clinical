package com.hms.api.patient.request;
import com.hms.domain.patient.model.Gender;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePatientRequest(
    String salutation,
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "First name must contain only alphabets") String firstName,
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Last name must contain only alphabets") String lastName,
    Gender gender,
    @PastOrPresent LocalDate dateOfBirth, @PastOrPresent LocalDate estimatedDateOfBirth,
    String contactNumber, String email,
    @Size(max = 10, message = "Blood group must be at most 10 characters") String bloodGroup,
    String address,
    UUID primaryProviderId, UUID areaId, boolean isClinicalTrial
) {}
