package com.hms.api.billing.response;
import com.hms.domain.billing.model.ChargeLineStatus;
import java.time.Instant;
import java.util.UUID;
public record ChargeLineItemResponse(
    UUID id, UUID serviceCatalogItemId, String itemName, UUID pricingTierId,
    long amount, long unitRate, int quantity, boolean quantitative,
    long discountAmount, long disallowedAmount,
    ChargeLineStatus status, Instant bedChargeFrom, Instant bedChargeTo,
    String cancelReason, Instant createdAt,
    UUID pharmacySaleId
) {}
