package com.hms.infrastructure.persistence.mfa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The short-lived handle between "password accepted" and "second factor
 * accepted".
 *
 * <p>Server-side rather than a signed token held by the client, so that the
 * attempt counter cannot be discarded by dropping the token and starting again,
 * and so revoking an in-flight challenge is a DELETE.
 *
 * <p>Carries the branch chosen in the first step. Asking the user to choose
 * again after entering their code would be a second decision point for no
 * reason, and re-deriving it would risk landing them somewhere they did not pick.
 */
@Entity
@Table(name = "mfa_challenges")
@Getter
@Setter
public class MfaChallengeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "force_logout", nullable = false)
    private boolean forceLogout = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isUsable() {
        return consumedAt == null && expiresAt.isAfter(Instant.now());
    }
}
