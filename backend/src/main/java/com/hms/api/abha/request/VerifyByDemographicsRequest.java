package com.hms.api.abha.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Aadhaar demographic verification — the fallback when no mobile is linked to
 * the patient's Aadhaar, so no OTP can reach them.
 *
 * <p>Year of birth rather than a full date: that is what UIDAI matches on, and
 * asking for a precise date would collect more than is needed.
 */
public record VerifyByDemographicsRequest(

    @NotBlank(message = "aadhaar is required")
    @Pattern(regexp = "\\d{12}", message = "aadhaar must be 12 digits")
    String aadhaar,

    @NotBlank(message = "name is required") String name,

    @NotBlank(message = "gender is required")
    @Pattern(regexp = "M|F|O", message = "gender must be M, F or O") String gender,

    @NotBlank(message = "yearOfBirth is required")
    @Pattern(regexp = "\\d{4}", message = "yearOfBirth must be four digits") String yearOfBirth
) {
}
