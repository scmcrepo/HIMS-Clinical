package com.hms.api.claims.response;

import com.hms.infrastructure.persistence.payment.ClaimDeductionLineEntity;

import java.util.UUID;

/** One disallowed item, itemised so billing can challenge it specifically. */
public record DeductionLineResponse(
    UUID id,
    String reasonCategory,
    String reasonCode,
    String description,
    Long amount,
    boolean disputed,
    String disputeNote
) {
    public static DeductionLineResponse from(ClaimDeductionLineEntity e) {
        return new DeductionLineResponse(e.getId(), e.getReasonCategory(), e.getReasonCode(),
                                         e.getDescription(), e.getAmount(), e.isDisputed(),
                                         e.getDisputeNote());
    }
}
