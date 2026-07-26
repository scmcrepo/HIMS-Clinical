package com.hms.api.hitl.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.infrastructure.persistence.hitl.HitlEscalationEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param transcript included only in the single-item view. The queue listing
 *                   omits it: rendering every waiting patient's conversation
 *                   into a list payload multiplies PHI exposure for no benefit,
 *                   since the operator reads one at a time.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EscalationResponse(
    UUID id,
    String runId,
    String correlationId,
    UUID tenantId,
    UUID branchId,
    String channel,
    String reason,
    String detail,
    String intent,
    Double confidence,
    List<Map<String, Object>> proposedActions,
    String state,
    Instant raisedAt,
    Instant expiresAt,
    Instant resolvedAt,
    String operatorAction,
    List<Map<String, Object>> transcript
) {

    public static EscalationResponse summary(HitlEscalationEntity e) {
        return build(e, null);
    }

    public static EscalationResponse detail(HitlEscalationEntity e,
                                            List<Map<String, Object>> transcript) {
        return build(e, transcript);
    }

    private static EscalationResponse build(HitlEscalationEntity e,
                                            List<Map<String, Object>> transcript) {
        return new EscalationResponse(
            e.getId(), e.getRunId(), e.getCorrelationId(), e.getTenantId(), e.getBranchId(),
            e.getChannel(), e.getReason(), e.getDetail(), e.getIntent(),
            e.getConfidence() == null ? null : e.getConfidence().doubleValue(),
            e.getProposedActions(), e.getState(), e.getCreatedAt(), e.getExpiresAt(),
            e.getResolvedAt(), e.getOperatorAction(), transcript);
    }
}
