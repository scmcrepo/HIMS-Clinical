package com.hms.api.catalog.request;
import com.hms.domain.billing.model.BillType;
import com.hms.domain.catalog.model.ServiceType;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record CreateServiceItemRequest(
    @NotBlank String name,
    @NotNull UUID categoryId,
    @NotNull ServiceType serviceType,
    boolean requiresOrder,
    @NotNull @Size(min=1) List<PricingTierRequest> pricingTiers
) {
    public record PricingTierRequest(@NotNull BillType billType, @NotNull @PositiveOrZero long unitRate) {}
}
