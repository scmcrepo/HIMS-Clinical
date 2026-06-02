package com.hms.api.billing.response;
import com.hms.domain.billing.model.*;
import java.time.Instant;
import java.util.UUID;
public record PaymentResponse(
    UUID id, long amount, PaymentMode paymentMode,
    PaymentType paymentType, Instant recordedAt,
    String sequenceNumber, String notes
) {}
