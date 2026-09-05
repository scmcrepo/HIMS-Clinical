package com.hms.infrastructure.persistence.mfa;

import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's enrolled TOTP second factor (WO-029 / U-002).
 *
 * <p>Deliberately not an {@code AuditableEntity}. This row is read during login,
 * before authentication completes, when there is no TenantContext — so the
 * tenant filter would be inert and {@code @PrePersist} would stamp a null
 * tenant. Same reasoning as {@code PasswordResetOtpEntity}. The tenant is stored
 * as a plain column for investigation, not for scoping.
 */
@Entity
@Table(name = "user_mfa_credentials")
@Getter
@Setter
public class UserMfaCredentialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    /**
     * The TOTP shared secret, AES-256-GCM encrypted at rest.
     *
     * <p>Treat as a credential rather than as data: anyone who can read it can
     * mint valid codes indefinitely, and unlike a password it is never rotated
     * by ordinary use. It is never returned by any endpoint after enrolment.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret", nullable = false, columnDefinition = "TEXT")
    private String secret;

    /**
     * When the user first proved they could generate a code from this secret.
     *
     * <p>Null means enrolment was started and never finished. An unconfirmed
     * credential must not satisfy a login challenge and must not count as
     * coverage — otherwise a user who scanned a QR code and closed the tab would
     * be locked out the moment the mode became REQUIRED.
     */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** RFC 6238 step of the last accepted code. Replay guard; see MfaService. */
    @Column(name = "last_time_step")
    private Long lastTimeStep;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.modifiedAt = Instant.now();
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
