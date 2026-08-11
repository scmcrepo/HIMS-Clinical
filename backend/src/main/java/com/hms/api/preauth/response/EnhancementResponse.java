package com.hms.api.preauth.response;

import com.hms.infrastructure.persistence.preauth.PreAuthEnhancementEntity;

import java.time.Instant;
import java.util.UUID;

/** An enhancement request and its outcome — Screen 4.4. */
public record EnhancementResponse(
    UUID id,
    Integer sequenceNumber,
    Long previousApproved,
    Long revisedEstimate,
    Long requestedDelta,
    String justification,
    String enhancementState,
    Long approvedAmount,
    Instant respondedAt
) {
    public static EnhancementResponse from(PreAuthEnhancementEntity e) {
        return new EnhancementResponse(
            e.getId(), e.getSequenceNumber(), e.getPreviousApproved(), e.getRevisedEstimate(),
            e.getRevisedEstimate() - e.getPreviousApproved(),
            e.getJustification(), e.getEnhancementState(), e.getApprovedAmount(),
            e.getRespondedAt());
    }
}
