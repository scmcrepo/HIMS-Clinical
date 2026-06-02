package com.hms.api.billing.request;
import com.hms.domain.billing.model.*;
import jakarta.validation.constraints.*;
import java.util.List;
public record RecordPaymentRequest(@NotNull @Size(min=1) List<PaymentEntry> payments) {
    public record PaymentEntry(
        @NotNull @Positive long amount,
        @NotNull PaymentMode paymentMode,
        @NotNull PaymentType paymentType,
        String notes
    ) {}
}
