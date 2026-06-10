package com.hms.api.patient.request;
import com.hms.domain.patient.model.Gender;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterPatientRequest(
    String salutation,
    @NotBlank @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "First name must contain only alphabets") String firstName,
    @NotBlank @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Last name must contain only alphabets") String lastName,
    @NotNull Gender gender,
    @PastOrPresent LocalDate dateOfBirth,
    @NotNull @PastOrPresent LocalDate estimatedDateOfBirth,
    @NotBlank(message = "Contact number is required") @Pattern(regexp = "\\d{10}", message = "Contact number must be 10 digits") String contactNumber,
    @Email(message = "Invalid email format") String email,
    @Size(max = 10, message = "Blood group must be at most 10 characters") String bloodGroup,
    String address,
    UUID primaryProviderId,
    UUID areaId,
    UUID categoryId,
    boolean isClinicalTrial,
    boolean createEncounter
) {}
