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
 * One issued refresh token. Rotation appends a child row and marks the parent
 * consumed, so a chain is its own audit trail.
 *
 * <p>Like {@link PortalOtpChallengeEntity}, this does <b>not</b> extend
 * {@code AuditableEntity}: a refresh request carries no session and no tenant
 * context, so the row has to be readable before scope exists. {@code tenantId}
 * is stored as the data used to build the principal afterwards, not as a filter
 * column — which is precisely why every read here is by unique token hash and
 * never by patient or tenant alone.
 */
@Entity
@Table(name = "portal_sessions")
@Getter
@Setter
public class PortalSessionEntity {

    /** Reasons a chain can be cut short. Mirrors ck_portal_session_revoked_reason. */
    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";
    public static final String REASON_DEVICE_LIMIT = "DEVICE_LIMIT";
    public static final String REASON_CONSENT_WITHDRAWN = "CONSENT_WITHDRAWN";
    public static final String REASON_ERASURE = "ERASURE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Stable across rotations; identifies one device's login. */
    @Column(name = "chain_id", nullable = false)
    private UUID chainId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    /** SHA-256 hex of the refresh token. The plaintext is never stored. */
    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 40)
    private String revokedReason;

    public boolean isLive(Instant now) {
        return consumedAt == null && revokedAt == null && now.isBefore(expiresAt);
    }
}
