package com.hms.api.goods.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public record ReceiveGoodsRequest(
    UUID supplierId, UUID purchaseOrderId, @NotNull UUID departmentId, String invoiceNumber, LocalDate invoiceDate, String notes,
    @NotNull @Size(min=1) List<ReceiveLine> lines
) {
    public record ReceiveLine(
        @NotNull UUID itemId,
        String batchNumber,
        @Positive int quantity,
        @NotNull BigDecimal purchaseRate,
        @NotNull BigDecimal maximumRetailPrice,
        @NotNull BigDecimal sellingRate,
        LocalDate expiryDate,
        Integer freeQty
    ) {}
}
