package com.hms.api.patient.request;
import com.hms.domain.patient.model.Gender;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterPatientRequest(
    String salutation,
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull Gender gender,
    @PastOrPresent LocalDate dateOfBirth,
    @NotNull @PastOrPresent LocalDate estimatedDateOfBirth,
    @NotBlank(message = "Contact number is required") @Pattern(regexp = "\\d{10}", message = "Contact number must be 10 digits") String contactNumber,
    @Email(message = "Invalid email format") String email,
    String bloodGroup,
    String address,
    UUID primaryProviderId,
    UUID areaId,
    UUID categoryId,
    boolean isClinicalTrial,
    boolean createEncounter
) {}
