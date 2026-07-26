package com.hms.api.agent.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hms.infrastructure.persistence.agent.AgentApiTokenEntity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * @param token plaintext, present only in the response to issuance. Every later
 *              read returns null here — there is no recovery path, only reissue.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentTokenResponse(
    UUID id,
    String name,
    Set<String> scopes,
    UUID branchId,
    Instant createdAt,
    Instant expiresAt,
    Instant lastUsedAt,
    Instant revokedAt,
    String status,
    String token
) {

    public static AgentTokenResponse from(AgentApiTokenEntity e) {
        return build(e, null);
    }

    public static AgentTokenResponse withPlaintext(AgentApiTokenEntity e, String plaintext) {
        return build(e, plaintext);
    }

    private static AgentTokenResponse build(AgentApiTokenEntity e, String plaintext) {
        String status = e.isRevoked() ? "REVOKED"
            : e.isExpired(Instant.now()) ? "EXPIRED" : "ACTIVE";
        return new AgentTokenResponse(
            e.getId(), e.getName(), e.getScopes(), e.getBranchId(),
            e.getCreatedAt(), e.getExpiresAt(), e.getLastUsedAt(), e.getRevokedAt(),
            status, plaintext);
    }
}
