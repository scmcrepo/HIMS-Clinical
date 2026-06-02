package com.hms.api.billing.response;
import com.hms.domain.billing.model.*;
import java.time.LocalDate;
import java.util.UUID;
public record BillSummaryResponse(
    UUID id, UUID patientId, String patientName, String patientNumber, UUID encounterId,
    long billAmount, long dueAmount, long discountTotal, long discountRefundTotal,
    BillStatus status, BillType billType, EncounterType encounterType,
    java.time.LocalDate billDate, String billNumber, java.time.Instant createdAt,
    long refundTotal
) {}
