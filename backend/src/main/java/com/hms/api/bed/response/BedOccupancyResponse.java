package com.hms.api.bed.response;
import java.time.Instant;
import java.util.UUID;
public record BedOccupancyResponse(
    UUID id,
    UUID bedId,
    UUID encounterId,
    UUID billId,
    Instant fromDatetime,
    Instant toDatetime,
    boolean isActive
) {}
