package com.hms.infrastructure.persistence.shared;

import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/**
 * Password reset runs before authentication, so there is no tenant context to
 * scope by and the lookup is necessarily platform-level. NOTE (WO-028, card
 * F-001): two tenants holding a user at the same address has not been
 * reasoned through, and this table also stores that address in plaintext.
 * Both are open.
 */
@Entity
@Table(name = "password_reset_otp")
@Getter
@Setter
public class PasswordResetOtpEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The email the OTP was sent to. Encrypted (F-001).
     *
     * <p>Never query on this column. {@code EncryptedStringConverter} is
     * non-deterministic — the same address encrypts differently every time — so
     * a {@code findByEmail} would compile, run, and silently match nothing.
     * That is exactly what would have happened had this been encrypted during
     * WO-028 without the token below, and password reset would have broken for
     * every user with no error to explain why.
     *
     * <p>Use {@link #emailToken}.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", nullable = false, columnDefinition = "TEXT")
    private String email;

    /**
     * Deterministic HMAC of the lowercased email, for lookup.
     *
     * <p>Same pattern as {@code patients.contact_number_token}: ciphertext in
     * one column, a stable token in another, queries against the token. The
     * token is not reversible, so it discloses nothing on its own, but it is
     * equality-comparable, which is all the reset flow needs.
     */
    @Column(name = "email_token", length = 64)
    private String emailToken;

    @Column(name = "otp", nullable = false, length = 6)
    private String otp;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
