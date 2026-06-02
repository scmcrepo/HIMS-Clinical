package com.hms.api.insurance.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
public record PreAuthRequest(
    @NotBlank String preAuthNumber,
    @NotNull @Positive long amount,
    @NotNull LocalDate receivedDate
) {}
