package com.hms.api.opip.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VisitDiagnosticOrderResponse(
    UUID                              id,
    UUID                              encounterId,
    UUID                              requestedById,
    String                            requestedByName,
    Instant                           orderedAt,
    List<DiagnosticOrderLineResponse> items
) {
    public record DiagnosticOrderLineResponse(
        UUID   id,
        String diagnosticTestId,
        String testName,
        String category,
        String status   // ORDERED | COLLECTED | RESULTED
    ) {}
}
