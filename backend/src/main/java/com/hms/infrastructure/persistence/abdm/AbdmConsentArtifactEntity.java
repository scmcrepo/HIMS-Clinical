package com.hms.infrastructure.persistence.abdm;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A signed consent artifact issued by the Consent Manager.
 *
 * <p>The signature is retained because it is the hospital's proof that a grant
 * was genuine. Without it there is no way to later show why another provider's
 * records were held.
 */
@Entity
@Table(name = "abdm_consent_artifacts")
@Getter
@Setter
public class AbdmConsentArtifactEntity extends AuditableEntity {

    @Column(name = "consent_request_id", nullable = false)
    private UUID consentRequestId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "artifact_id", nullable = false, length = 80)
    private String artifactId;

    @Column(name = "signature", columnDefinition = "TEXT")
    private String signature;

    @Column(name = "hip_id", length = 80)
    private String hipId;

    @Column(name = "hip_name", length = 200)
    private String hipName;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** GRANTED | EXPIRED | REVOKED. Derive the live value via ConsentArtifactRules. */
    @Column(name = "artifact_state", nullable = false, length = 20)
    private String artifactState = "GRANTED";
}
