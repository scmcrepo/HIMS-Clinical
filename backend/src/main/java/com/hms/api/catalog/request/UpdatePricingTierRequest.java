package com.hms.api.catalog.request;
import com.hms.domain.billing.model.BillType;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record UpdatePricingTierRequest(@NotNull UUID tierId, @NotNull BillType billType, @NotNull @PositiveOrZero long unitRate) {}
