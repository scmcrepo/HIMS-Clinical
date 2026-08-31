package com.hms.infrastructure.persistence.grievance;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One data protection grievance — DPDP s. 8(9) and s. 13.
 *
 * <p>The Rules set a 90-day ceiling on resolution. This entity carries both
 * {@link #targetAt} and {@link #dueAt} because a complaint answered on day 89 is
 * compliant and is also a bad outcome; keeping them apart stops the statutory
 * maximum quietly becoming the working norm.
 *
 * <p>{@link #body} and {@link #resolution} are encrypted. A complaint is a
 * person's account of their own care in their own words, routinely with clinical
 * detail, and it is given to us specifically by someone exercising a right —
 * which makes storing it in plaintext a poor first response.
 */
@Entity
@Table(name = "grievances")
@Getter
@Setter
public class GrievanceEntity extends AuditableEntity {

    @Column(name = "grievance_ref", nullable = false, length = 30)
    private String grievanceRef;

    /**
     * Null when the complainant cannot yet be matched to a patient record.
     *
     * <p>Deliberately permitted. Requiring a match before a complaint can be
     * recorded would be a tidy way of never recording the inconvenient ones.
     */
    @Column(name = "patient_id")
    private UUID patientId;

    /** Contact details for an unmatched complainant. Personal data, encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "complainant_contact", columnDefinition = "TEXT")
    private String complainantContact;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    /** Internal target, earlier than the statutory ceiling. */
    @Column(name = "target_at", nullable = false)
    private Instant targetAt;

    /** Statutory ceiling. */
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    /** RECEIVED | ACKNOWLEDGED | IN_PROGRESS | RESOLVED | CLOSED | WITHDRAWN */
    @Column(name = "state", nullable = false, length = 20)
    private String state = "RECEIVED";

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** What the complainant was told. Encrypted. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    /**
     * The complainant went to the Board.
     *
     * <p>Not a failure state on its own — they are entitled to at any point —
     * but the rate is the truest measure of whether the mechanism is effective
     * rather than merely present.
     */
    @Column(name = "escalated_to_board", nullable = false)
    private boolean escalatedToBoard;

    @Column(name = "board_reference", length = 80)
    private String boardReference;

    /** Set when a complaint turns out to be about a breach. Often the first sign. */
    @Column(name = "incident_id")
    private UUID incidentId;

    /** True once the statutory deadline has passed with the matter unresolved. */
    public boolean isOverdue(Instant now) {
        return dueAt != null && now.isAfter(dueAt) && isOpen();
    }

    public boolean isOpen() {
        return !"RESOLVED".equals(state) && !"CLOSED".equals(state) && !"WITHDRAWN".equals(state);
    }
}
