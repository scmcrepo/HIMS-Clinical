package com.hms.api.opip.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
    UUID                   id,
    UUID                   encounterId,
    UUID                   requestedById,
    String                 requestedByName,
    Instant                createdAt,
    List<PrescriptionLineResponse> items
) {
    public record PrescriptionLineResponse(
        UUID   id,
        String drugItemId,
        String drugName,
        String frequency,
        String duration,
        int    qty,
        String instructionId,
        String instructionLabel,
        String routeId,
        String routeLabel,
        String remarks
    ) {}
}
