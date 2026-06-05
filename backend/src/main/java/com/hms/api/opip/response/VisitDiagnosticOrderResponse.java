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
    List<DiagnosticOrderLineResponse> items,
    UUID                              realOrderId,
    String                            diagnosticType
) {
    public VisitDiagnosticOrderResponse(
        UUID                              id,
        UUID                              encounterId,
        UUID                              requestedById,
        String                            requestedByName,
        Instant                           orderedAt,
        List<DiagnosticOrderLineResponse> items
    ) {
        this(id, encounterId, requestedById, requestedByName, orderedAt, items, null, null);
    }

    public record DiagnosticOrderLineResponse(
        UUID    id,
        String  diagnosticTestId,
        String  testName,
        String  category,
        String  status,   // ORDERED | COLLECTED | RESULTED
        Boolean isApproved,
        UUID    realOrderLineId
    ) {
        public DiagnosticOrderLineResponse(
            UUID   id,
            String diagnosticTestId,
            String testName,
            String category,
            String status
        ) {
            this(id, diagnosticTestId, testName, category, status, false, null);
        }
    }
}
