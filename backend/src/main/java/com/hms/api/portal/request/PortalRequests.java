package com.hms.api.portal.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hms.domain.patient.model.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Portal request payloads.
 *
 * <p>Note what none of these carry: a {@code patientId} on any read, and a
 * {@code tenantId} on anything after login. Those come from the token. The
 * requirement document's {@code GET /portal/appointments?patientId=} is the
 * shape of an IDOR, and the cheapest way to keep it from reappearing during
 * review is for the field to not exist in the type at all.
 */
public final class PortalRequests {

    private PortalRequests() {}

    /** Indian mobile numbering: subscriber numbers begin 6-9, exactly ten digits. */
    private static final String MOBILE_PATTERN = "^[6-9]\\d{9}$";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtpRequest(
        @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = "Enter a valid 10-digit mobile number")
        String mobile
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtpVerify(
        @NotNull UUID challengeId,
        @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = "Enter a valid 10-digit mobile number")
        String mobile,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "The code is 6 digits")
        String code
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionExchange(
        @NotNull UUID patientId,
        @NotNull UUID tenantId,
        @NotNull UUID branchId,
        @Size(max = 120) String deviceLabel
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RefreshRequest(
        @NotBlank String refreshToken
    ) {}

    /**
     * Self-registration.
     *
     * <p>The name patterns match {@code RegisterPatientRequest} exactly —
     * {@code ^[a-zA-Z\s]+$}, letters and spaces only. They are duplicated rather
     * than referenced because a client that accepts what the server rejects
     * produces a form the patient cannot submit and cannot understand. Note this
     * is stricter than the requirement document's proposed pattern, which
     * allowed dots and hyphens: the server is the authority and it does not.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SelfRegister(
        @NotNull UUID tenantId,
        @NotNull UUID branchId,
        String salutation,
        @NotBlank @Size(max = 60)
        @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "First name must contain only alphabets")
        String firstName,
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Last name must contain only alphabets")
        String lastName,
        @NotNull Gender gender,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = "Enter a valid 10-digit mobile number")
        String mobile,
        @Email String email,
        @Size(max = 10) String bloodGroup,
        @Size(max = 500) String address,
        /** Version of the consent text shown in the app. Recorded, not trusted for display. */
        @NotBlank String consentVersion
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateProfile(
        @NotBlank @Size(max = 60)
        @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "First name must contain only alphabets")
        String firstName,
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Last name must contain only alphabets")
        String lastName,
        @NotNull Gender gender,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotBlank @Pattern(regexp = MOBILE_PATTERN, message = "Enter a valid 10-digit mobile number")
        String mobile,
        @Email String email,
        @Size(max = 10) String bloodGroup,
        @Size(max = 500) String address
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookAppointment(
        @NotNull UUID providerId,
        @NotNull UUID slotId,
        @NotNull LocalDate appointmentDate,
        String notes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CancelAppointment(
        String reason
    ) {}
}
