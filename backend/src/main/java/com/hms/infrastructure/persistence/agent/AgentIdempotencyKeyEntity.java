package com.hms.infrastructure.persistence.agent;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * De-duplication record for a retried write tool.
 *
 * <p>The cached response body is encrypted at rest: a stored {@code book_slot}
 * response contains appointment and patient detail, and a plaintext cache of tool
 * responses would be a PII store nobody thinks of as one. The 24h TTL keeps that
 * copy short-lived so a DPDP erasure request does not have to chase it.
 */
@Entity
@Table(name = "agent_idempotency_keys")
@Getter
@Setter
public class AgentIdempotencyKeyEntity extends AuditableEntity {

    /** SHA-256 hex of the caller-supplied key. */
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "tool_name", nullable = false, length = 80)
    private String toolName;

    /**
     * Hash of the request body. Lets us detect the pathological case of the same
     * idempotency key being reused for a *different* request, which is a caller
     * bug worth surfacing rather than silently replaying the wrong response.
     */
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
