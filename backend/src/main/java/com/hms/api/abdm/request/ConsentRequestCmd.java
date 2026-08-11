package com.hms.api.abdm.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Request patient consent to view records held elsewhere — Screen 3.1.
 *
 * <p>{@code expiresAt} is required rather than defaulted. A consent with no end
 * date is not a thing ABDM should be asked for, and picking a default here would
 * quietly decide how long the hospital keeps another provider's records.
 */
public record ConsentRequestCmd(
    @NotNull(message = "patientId is required") UUID patientId,
    UUID encounterId,
    /** CAREMGT | BTG | PUBHLTH | HPAYMT | DSRCH | PATRQT */
    @NotBlank(message = "purposeCode is required") String purposeCode,
    @NotEmpty(message = "select at least one record type") Set<String> hiTypes,
    @NotNull(message = "dateRangeFrom is required") LocalDate dateRangeFrom,
    @NotNull(message = "dateRangeTo is required") LocalDate dateRangeTo,
    @NotNull(message = "expiresAt is required") Instant expiresAt
) {}
