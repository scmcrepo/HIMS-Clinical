package com.hms.infrastructure.persistence.compliance;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * The exact notice text shown to a patient, per tenant, purpose, version and
 * language.
 *
 * <p>Before this existed, {@code consent_records.notice_text_hash} was the
 * SHA-256 of {@code ConsentPurpose.getNoticeSummary()} — a developer-authored
 * English UI label compiled into the jar. A hash whose preimage cannot be
 * produced on demand proves nothing at all, and "what exactly was this patient
 * shown, on what date, in what language" is the question an audit actually asks.
 *
 * <p>The text is hospital copy, not patient data: it is deliberately not
 * encrypted, carries no personal data, and is out of scope for erasure.
 *
 * <p>Rows seeded by V205 are {@code DRAFT}. They carry over the old enum
 * summaries so the desk is not blocked on day one, but they are <b>not adequate
 * DPDP notices</b> — they state no retention period, no recipients and no
 * withdrawal method. Replacing them with counsel-approved wording is a data
 * change, not a deployment.
 */
@Entity
@Table(name = "consent_notices")
@Getter
@Setter
public class ConsentNoticeEntity extends AuditableEntity {

    @Column(name = "purpose", nullable = false, length = 40)
    private String purpose;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "language", nullable = false, length = 10)
    private String language = "en";

    @Column(name = "body_text", nullable = false, columnDefinition = "TEXT")
    private String bodyText;

    /** DRAFT | ACTIVE | SUPERSEDED. Only ACTIVE may be shown to a patient. */
    @Column(name = "notice_state", nullable = false, length = 10)
    private String noticeState = "DRAFT";

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom = Instant.now();

    @Column(name = "effective_to")
    private Instant effectiveTo;

    /** Whether this notice is the one currently in force. */
    public boolean isLive(Instant now) {
        return effectiveFrom != null
            && !now.isBefore(effectiveFrom)
            && (effectiveTo == null || now.isBefore(effectiveTo));
    }
}
