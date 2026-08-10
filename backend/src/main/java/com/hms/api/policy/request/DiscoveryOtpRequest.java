package com.hms.api.policy.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Ask the registry to send the patient an OTP authorising a policy lookup. */
public record DiscoveryOtpRequest(
    @NotNull(message = "patientId is required") UUID patientId,
    /** ABHA address or 10-digit mobile. Forwarded to NHCX, never stored. */
    @NotBlank(message = "identifier is required") String identifier
) {
}
