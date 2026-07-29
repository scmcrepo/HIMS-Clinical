package com.hms.api.sales.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record CreateSaleRequest(
    UUID id,
    UUID patientId,
    String customerName,
    String customerPhone,
    String consultantName,
    UUID encounterId,
    java.time.Instant prescribedAt,
    @NotNull UUID departmentId,
    boolean isDraft,
    BigDecimal discountAmount,
    String discountType,
    BigDecimal discountValue,
    @NotNull @Size(min=1) List<SaleLine> lines,
    String paymentMode,
    String cardType,
    String cardNumber,
    String bankName,
    BigDecimal paidAmount
) {
    public record SaleLine(
        @NotNull UUID inventoryBatchId,
        @NotNull @Positive int quantity,
        @NotNull BigDecimal unitRate,
        BigDecimal discountAmount,
        String discountType,
        BigDecimal discountValue
    ) {}
}
