package com.hms.api.opip.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AddDiagnosticOrderRequest(
    @NotNull @NotEmpty List<DiagnosticOrderLineRequest> items,
    UUID requestedById   // IP: consultant requesting; OP: implicit (can be null)
) {
    public record DiagnosticOrderLineRequest(
        String diagnosticTestId,    // FK to DiagnosticTest catalog (nullable if free-text)
        String testName,            // display name / free-text fallback
        String category             // LAB or RADIOLOGY — optional hint
    ) {}
}
