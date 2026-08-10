package com.hms.api.abha.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Verify the OTP issued by {@code POST /abha/enrolment}. */
public record VerifyAbhaOtpRequest(

    @NotBlank(message = "otp is required")
    @Pattern(regexp = "\\d{4,8}", message = "otp must be 4 to 8 digits")
    String otp,

    /** Optional mobile to attach to the new ABHA. Forwarded, never stored here. */
    @Pattern(regexp = "|\\d{10}", message = "mobile must be 10 digits")
    String mobile
) {
}
