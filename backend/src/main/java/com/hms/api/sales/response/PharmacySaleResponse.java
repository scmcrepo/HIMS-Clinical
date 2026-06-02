package com.hms.api.sales.response;
import com.hms.domain.sales.model.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record PharmacySaleResponse(
    UUID id, UUID patientId, String patientName, 
    String customerName, String customerPhone, String consultantName,
    UUID encounterId, UUID departmentId,
    String sequenceNumber, LocalDate saleDate,
    BigDecimal totalAmount, BigDecimal discountAmount,
    SaleStatus status,
    List<SaleLineResponse> lines,
    java.time.Instant createdAt,
    String paymentMode,
    String cardType,
    String cardNumber,
    String bankName,
    BigDecimal paidAmount,
    BigDecimal dueAmount,
    List<PaymentResponse> payments,
    String patientNumber
) {
    public record SaleLineResponse(UUID id, UUID inventoryBatchId, int quantity, BigDecimal unitRate, BigDecimal amount, BigDecimal discountAmount) {}
    public record PaymentResponse(UUID id, BigDecimal amount, String paymentMode, String cardType, String cardNumber, String bankName, java.time.Instant createdAt) {}
}
