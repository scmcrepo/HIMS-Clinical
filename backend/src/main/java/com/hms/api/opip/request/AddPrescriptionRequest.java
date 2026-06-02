package com.hms.api.opip.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record AddPrescriptionRequest(
    @NotNull List<PrescriptionLineRequest> items,
    UUID requestedById   // IP: consultant requesting; OP: implicit (can be null)
) {
    public record PrescriptionLineRequest(
        @NotBlank String drugItemId,
        @NotBlank String drugName,        // denormalised display name
        @NotBlank String frequency,       // e.g. "1-0-1"
        @NotBlank String duration,        // e.g. "5 days"
        @NotNull  @Positive int qty,
        String instructionId,             // optional FK to InstructionMaster
        String instructionLabel,          // optional display
        String routeId,                   // optional FK to RouteMaster
        String routeLabel,                // optional display
        String remarks                    // free-text precautions / contraindications
    ) {}
}
