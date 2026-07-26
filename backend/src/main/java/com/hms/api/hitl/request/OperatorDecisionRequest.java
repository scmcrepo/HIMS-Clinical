package com.hms.api.hitl.request;

import jakarta.validation.constraints.NotBlank;

/**
 * @param reason mandatory for CORRECT and OVERRIDE — enforced in the service,
 *               because it is the audit record of why a human disagreed and the
 *               signal used to improve the agent. A UI-only check would be
 *               bypassed by the first script anyone writes.
 */
public record OperatorDecisionRequest(
    @NotBlank String action,
    String reason,
    String reply
) {
}
