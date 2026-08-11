package com.hms.api.preauth.response;

import com.hms.infrastructure.persistence.preauth.PreAuthEstimateLineEntity;

import java.math.BigDecimal;
import java.util.UUID;

/** One estimate line. Amounts in paise; the frontend formats. */
public record EstimateLineResponse(
    UUID id,
    String category,
    String description,
    BigDecimal quantity,
    Long unitAmount,
    Long lineAmount,
    Long approvedAmount
) {
    public static EstimateLineResponse from(PreAuthEstimateLineEntity e) {
        return new EstimateLineResponse(e.getId(), e.getCategory(), e.getDescription(),
                                        e.getQuantity(), e.getUnitAmount(), e.getLineAmount(),
                                        e.getApprovedAmount());
    }
}
