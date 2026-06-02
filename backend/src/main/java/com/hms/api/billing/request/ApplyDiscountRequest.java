package com.hms.api.billing.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record ApplyDiscountRequest(
    @NotNull @PositiveOrZero long totalDiscount,
    @NotNull @Size(min=1) List<@Valid LineDiscountEntry> lineDiscounts,
    String reason
) {
    public record LineDiscountEntry(@NotNull UUID chargeLineItemId, @NotNull @PositiveOrZero long amount) {}
}
