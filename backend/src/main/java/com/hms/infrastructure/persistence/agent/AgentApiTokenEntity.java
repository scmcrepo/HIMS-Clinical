package com.hms.infrastructure.persistence.agent;

import com.hms.domain.shared.model.AuditableEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A scoped machine credential for the AI agent layer.
 *
 * <p>Only the SHA-256 hash of the token is stored. The plaintext is returned once
 * at issue and is then unrecoverable — there is no reset path, only reissue. That
 * is deliberate: a credential the system can display on demand is a credential an
 * attacker can read out of the database.
 *
 * <p>Extends {@link AuditableEntity} so the Hibernate {@code tenantFilter} applies
 * automatically. Without it, every repository method here would have to remember
 * to scope itself, and one forgetful query becomes a cross-tenant credential leak.
 */
@Entity
@Table(name = "agent_api_tokens")
@Getter
@Setter
public class AgentApiTokenEntity extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** SHA-256 hex of the plaintext token. Never the token itself. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** Feature keys. Scopes ARE feature keys — one vocabulary, not two. */
    @Type(JsonType.class)
    @Column(name = "scopes", columnDefinition = "jsonb", nullable = false)
    private Set<String> scopes = new LinkedHashSet<>();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private java.util.UUID revokedBy;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
