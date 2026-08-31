package com.hms.api.preauth.request;

import com.hms.api.shared.ConsentAttestation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Raise a cashless pre-authorisation — Screen 4.1.
 *
 * <p>No total is accepted from the client. It is computed from the lines, so the
 * figure sent to the insurer always equals the figure shown on screen.
 */
public record SubmitPreAuthCmd(
    @NotNull(message = "patientId is required") UUID patientId,
    UUID encounterId,
    UUID insuranceId,
    @NotBlank(message = "payerCode is required") String payerCode,

    @NotBlank(message = "an ICD-10 diagnosis is required") String diagnosisCode,
    String diagnosisText,
    @NotBlank(message = "plannedProcedure is required") String plannedProcedure,
    @Min(value = 0, message = "length of stay cannot be negative") Integer expectedLosDays,
    String roomType,

    @NotEmpty(message = "add at least one estimate line") @Valid List<EstimateLine> lines,

    /**
     * Optional. Supplied only when the desk has just shown the patient the DPDP
     * notice and captured their agreement, in response to a prior 409
     * CONSENT_REQUIRED. Omitted when consent is already on file.
     */
    @Valid ConsentAttestation consent
) {

    /** Quantity is decimal: half a day of room rent and 1.5 implant units are real. */
    public record EstimateLine(
        @NotBlank String category,
        @NotBlank String description,
        @NotNull BigDecimal quantity,
        @PositiveOrZero long unitAmountPaise
    ) {}
}
