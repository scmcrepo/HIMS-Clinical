package com.hms.infrastructure.persistence.portal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A single SMS one-time code challenge.
 *
 * <p>This is a <b>platform-level</b> table: it has no tenant column and is not
 * tenant-scoped, because a login attempt happens before a hospital is chosen.
 *
 * <p><b>Deliberately does NOT extend {@code AuditableEntity}.</b> Every other
 * business entity does, which is what gives it the tenant and branch Hibernate
 * filters — but a challenge is created and read <em>before</em> a tenant is
 * known. The patient has typed a mobile number and nothing else; they have not
 * chosen a hospital, and the whole point of the lookup that follows is that it
 * spans hospitals. An entity extending {@code AuditableEntity} would fail its
 * {@code @PostLoad} scope assertion with {@code CrossTenantAccessException} on
 * exactly the read this class exists to serve.
 *
 * <p>There is no plaintext mobile column. The row is keyed by the same HMAC
 * token as {@code patients.contact_number_token}, so a dump of this table
 * reveals nothing about who was attempting to log in.
 */
@Entity
@Table(name = "portal_otp_challenges")
@Getter
@Setter
public class PortalOtpChallengeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * HMAC-SHA256 blind index of the mobile number, identical to
     * {@code patients.contact_number_token}.
     *
     * <p>Not encrypted, and must not be: this column exists to be matched
     * against on equality, and AES-GCM ciphertext differs on every write, so an
     * encrypted copy could never be looked up. The HMAC is already the
     * protection — it is one-way, and the number itself is never stored here in
     * any form.
     */
    @Column(name = "contact_number_token", nullable = false, length = 64)
    private String contactNumberToken;

    /** BCrypt hash. The plaintext code exists only in the SMS. */
    @Column(name = "code_hash", nullable = false, length = 72)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "max_attempts", nullable = false)
    private short maxAttempts;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean hasAttemptsLeft() {
        return attempts < maxAttempts;
    }

    /** Usable means: not spent, not expired, and still has guesses remaining. */
    public boolean isUsable(Instant now) {
        return !isConsumed() && !isExpired(now) && hasAttemptsLeft();
    }
}
