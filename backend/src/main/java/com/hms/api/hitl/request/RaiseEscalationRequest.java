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
    String detail,
    String intent,
    Double confidence,
    List<Map<String, Object>> transcript,
    List<Map<String, Object>> proposedActions,
    Integer timeoutSeconds
) {
}
