package com.hms.api.agent.v1;

/**
 * Stable, machine-readable failure codes for the agent tool surface.
 *
 * <p>The {@code retryable} flag is the important part. An LLM agent receiving a
 * bare 500 learns nothing except to loop or give up; told explicitly whether
 * retrying could help, it can back off, try an alternative, or escalate to a
 * human. These strings are a contract — rename one and the orchestrator's
 * handling silently stops matching.
 */
public enum AgentErrorCode {

    SLOT_FULL(false),
    SLOT_NOT_FOUND(false),
    PROVIDER_NOT_FOUND(false),
    PATIENT_NOT_FOUND(false),
    DUPLICATE_BOOKING(false),
    VALIDATION_FAILED(false),
    IDEMPOTENCY_KEY_REQUIRED(false),
    IDEMPOTENCY_KEY_REUSED(false),
    FORBIDDEN_SCOPE(false),
    UNAUTHORIZED(false),
    NOT_FOUND(false),
    RATE_LIMITED(true),
    UPSTREAM_UNAVAILABLE(true),
    INTERNAL_ERROR(true);

    private final boolean retryable;

    AgentErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
