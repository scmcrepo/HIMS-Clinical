package com.hms.api.billing.request;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public record AddChargeRequest(
    @NotNull UUID serviceCatalogItemId,
    UUID pricingTierId,
    @NotNull @Positive long unitRate,
    @NotNull @Positive int quantity,
    Instant bedChargeFrom,
    Instant bedChargeTo
) {}
