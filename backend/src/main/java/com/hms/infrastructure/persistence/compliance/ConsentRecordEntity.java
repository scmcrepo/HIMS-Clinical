package com.hms.infrastructure.persistence.compliance;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One patient's consent to one purpose.
 *
 * <p>Withdrawn records are never deleted. The row is the evidence that consent
 * existed, what the patient was shown, and when they revoked it — deleting it
 * would destroy exactly the audit trail the Act requires.
 */
@Entity
@Table(name = "consent_records")
@Getter
@Setter
public class ConsentRecordEntity extends AuditableEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "purpose", nullable = false, length = 40)
    private String purpose;

    /** GRANTED | WITHDRAWN | EXPIRED */
    @Column(name = "state", nullable = false, length = 20)
    private String state = "GRANTED";

    @Column(name = "notice_version", nullable = false, length = 20)
    private String noticeVersion;

    @Column(name = "notice_language", nullable = false, length = 10)
    private String noticeLanguage = "en";

    /**
     * Hash of the exact notice text shown. Storing the hash rather than the text
     * keeps the row small while still letting you prove, later, which wording a
     * given patient actually saw.
     */
    @Column(name = "notice_text_hash", length = 64)
    private String noticeTextHash;

    @Column(name = "capture_channel", nullable = false, length = 20)
    private String captureChannel;

    @Column(name = "captured_by")
    private UUID capturedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;

    @Column(name = "withdrawal_channel", length = 20)
    private String withdrawalChannel;

    @Column(name = "is_minor", nullable = false)
    private boolean minor = false;

    @Column(name = "guardian_verified", nullable = false)
    private boolean guardianVerified = false;

    public boolean isActive(Instant now) {
        if (!"GRANTED".equals(state)) {
            return false;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return false;
        }
        // A minor's consent without verified guardian approval is not consent.
        return !minor || guardianVerified;
    }
}
