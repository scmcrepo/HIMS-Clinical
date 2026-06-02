package com.hms.api.billing.request;

import com.hms.domain.billing.model.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BillingRequests {
    private BillingRequests() {}

    public record CreateBillRequest(
        @NotNull UUID patientId,
        UUID encounterId,
        UUID primaryProviderId,
        UUID payorId,
        UUID referralId,
        @NotNull BillType billType,
        @NotNull EncounterType encounterType,
        Instant admissionAt,
        String bedNumber
    ) {}

    public record AddChargeRequest(
        @NotNull UUID serviceCatalogItemId,
        UUID pricingTierId,
        UUID diagnosticOrderId,
        UUID pharmacySaleId,
        UUID packageGroupId,
        @Positive long amount,
        @Positive long unitRate,
        @Min(1) int quantity,
        LocalDate bedChargeFrom,
        LocalDate bedChargeTo
    ) {}

    public record RemoveChargeRequest(
        @NotNull UUID chargeLineItemId,
        String reason
    ) {}

    public record ApplyDiscountRequest(
        @Positive long totalDiscount,
        @NotNull List<LineDiscountItem> lineDiscounts,
        String reason
    ) {
        public record LineDiscountItem(
            @NotNull UUID chargeLineItemId,
            @Positive long amount
        ) {}
    }

    public record RecordPaymentRequest(
        @Positive long amount,
        @NotNull PaymentMode paymentMode,
        @NotNull PaymentType paymentType,
        String notes
    ) {}

    public record GenerateBillRequest(
        @NotNull LocalDate billDate,
        Instant dischargeAt
    ) {}

    public record RecordRefundRequest(
        @Positive long amount,
        @NotNull PaymentMode paymentMode,
        @NotNull List<UUID> lineItemIds,
        String notes
    ) {}
}
