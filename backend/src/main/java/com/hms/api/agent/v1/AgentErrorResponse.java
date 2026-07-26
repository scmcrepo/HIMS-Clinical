package com.hms.api.agent.v1;

/**
 * Error payload carried inside {@code ApiResponse.data} for agent endpoints.
 *
 * <p>Deliberately free of detail beyond the code: an agent does not need — and
 * must not be handed — the underlying exception text, which routinely contains
 * identifiers nobody intended to expose.
 */
public record AgentErrorResponse(String code, boolean retryable, String correlationId) {

    public static AgentErrorResponse of(AgentErrorCode code, String correlationId) {
        return new AgentErrorResponse(code.name(), code.isRetryable(), correlationId);
    }
}
