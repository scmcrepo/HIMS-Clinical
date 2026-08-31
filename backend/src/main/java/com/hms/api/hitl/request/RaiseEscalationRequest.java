package com.hms.api.hitl.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * Posted by the agent service when its graph interrupts.
 *
 * <p>Arrives on the agent-authenticated chain, so tenant and branch come from
 * the token rather than the body — a caller must not be able to file an
 * escalation into another hospital's queue by asserting a tenant id.
 */
public record RaiseEscalationRequest(
    @NotBlank String runId,
    String correlationId,
    @NotBlank String channel,
    @NotBlank String reason,
    /**
     * Which patient this escalation concerns, so an erasure request can reach the
     * transcript. Optional: an escalation can be raised before the caller has
     * been identified. Added in WO-024 — before it, transcripts held PHI with no
     * patient linkage at all and the erasure sweep could not target them.
     */
    java.util.UUID patientId,
    String detail,
    String intent,
    Double confidence,
    List<Map<String, Object>> transcript,
    List<Map<String, Object>> proposedActions,
    Integer timeoutSeconds
) {
}
