package com.hms.api.inventory.request;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record AdjustStockRequest(
    @NotNull UUID batchId,
    int adjustmentQty,
    String reason
) {}
