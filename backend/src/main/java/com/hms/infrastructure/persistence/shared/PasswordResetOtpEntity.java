package com.hms.infrastructure.persistence.shared;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_otp")
@Getter
@Setter
public class PasswordResetOtpEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "otp", nullable = false, length = 6)
    private String otp;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
