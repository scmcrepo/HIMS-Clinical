package com.hms.api.abha.request;

import com.hms.application.abha.AbhaService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Start an ABHA enrolment by sending an OTP.
 *
 * <p>{@code loginId} carries an Aadhaar number or a mobile number depending on
 * {@code channel}. It is forwarded to ABDM and never persisted, so it appears in
 * no response type. The patterns below reject obviously malformed input before
 * it reaches the gateway; they are not a substitute for ABDM's own validation.
 */
public record StartAbhaEnrolmentRequest(

    @NotNull(message = "patientId is required")
    UUID patientId,

    @NotNull(message = "channel is required")
    AbhaService.OtpChannel channel,

    @NotNull(message = "loginId is required")
    @Pattern(regexp = "\\d{10}|\\d{12}",
             message = "loginId must be a 10-digit mobile or 12-digit Aadhaar number")
    String loginId
) {
}
