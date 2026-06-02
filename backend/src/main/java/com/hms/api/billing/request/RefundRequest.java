package com.hms.api.billing.request;
import com.hms.domain.billing.model.PaymentMode;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
public record RefundRequest(
    @NotNull List<UUID> lineItemIds,
    @NotNull @Positive long amount,
    @NotNull PaymentMode paymentMode,
    String notes
) {}
