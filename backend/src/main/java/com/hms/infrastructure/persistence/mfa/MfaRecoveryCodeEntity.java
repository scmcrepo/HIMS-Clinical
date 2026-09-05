package com.hms.infrastructure.persistence.mfa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One single-use recovery code, stored as a BCrypt hash.
 *
 * <p>The plaintext is shown to the user exactly once, at enrolment, and is never
 * stored. A recovery code is a password equivalent that bypasses the second
 * factor entirely, so storing it recoverably would make the whole feature
 * decorative.
 */
@Entity
@Table(name = "mfa_recovery_codes")
@Getter
@Setter
public class MfaRecoveryCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "credential_id", nullable = false, updatable = false)
    private UUID credentialId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    /** Set the moment the code is accepted. Single use is the whole point. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
