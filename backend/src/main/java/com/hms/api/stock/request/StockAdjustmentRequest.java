package com.hms.api.stock.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record StockAdjustmentRequest(
    @NotNull UUID departmentId,
    String notes,
    @NotNull @Size(min = 1) List<LineRequest> lines
) {
    public record LineRequest(
        @NotNull UUID inventoryBatchId,
        int adjustmentQty,
        @NotNull String adjustmentType, // "ADD" or "SUBTRACT"
        String reason
    ) {}
}
