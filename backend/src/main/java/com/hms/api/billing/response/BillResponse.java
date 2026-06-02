package com.hms.api.billing.response;
import com.hms.domain.billing.model.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record BillResponse(
    UUID id, UUID patientId, UUID encounterId, UUID primaryProviderId,
    UUID payorId, UUID referralId,
    String patientName, String patientNumber, String patientGender, String consultantName,
    long billAmount, long discountTotal, long discountRefundTotal, long paymentTotal,
    long serviceRefundTotal, long refundTotal, long dueAmount,
    BillStatus status, BillType billType, EncounterType encounterType,
    LocalDate billDate, Instant admissionAt, Instant dischargeAt,
    String bedNumber, String billNumber, Instant cancelledAt, Instant createdAt,
    List<ChargeLineItemResponse> chargeLineItems,
    List<PaymentResponse> payments
) {}

