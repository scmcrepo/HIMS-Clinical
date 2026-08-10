package com.hms.api.policy.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** Confirm the patient's OTP, releasing the discovery result. */
public record ConfirmDiscoveryRequest(
    @NotNull(message = "patientId is required") UUID patientId,
    @NotBlank(message = "correlationId is required") String correlationId,
    @NotBlank(message = "otp is required")
    @Pattern(regexp = "\\d{4,8}", message = "otp must be 4 to 8 digits") String otp
) {
}
