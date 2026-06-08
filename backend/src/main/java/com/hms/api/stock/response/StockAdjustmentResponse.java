package com.hms.api.stock.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StockAdjustmentResponse(
    UUID id,
    UUID departmentId,
    String departmentName,
    String sequenceNumber,
    LocalDate adjustmentDate,
    String notes,
    String authorisedBy,
    Instant createdAt,
    List<LineResponse> lines
) {
    public record LineResponse(
        UUID id,
        UUID inventoryBatchId,
        String batchNumber,
        String itemName,
        int adjustmentQty,
        String adjustmentType,
        String reason
    ) {}
}
