package com.hms.api.goods.response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public record PurchaseReceiptResponse(
    UUID id, UUID supplierId, UUID purchaseOrderId, UUID departmentId, LocalDate receiptDate,
    String invoiceNumber, LocalDate invoiceDate, String notes, String sequenceNumber, List<LineResponse> lines
) {
    public record LineResponse(UUID id, UUID itemId, String batchNumber, int quantity,
        BigDecimal purchaseRate, BigDecimal maximumRetailPrice, BigDecimal sellingRate, LocalDate expiryDate) {}
}
