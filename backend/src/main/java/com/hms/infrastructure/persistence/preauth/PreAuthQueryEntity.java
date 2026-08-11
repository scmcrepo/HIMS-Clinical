package com.hms.infrastructure.persistence.preauth;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One round of an insurer query — Screen 4.3.
 *
 * <p>A thread, not a column. Insurers raise several rounds on one pre-auth, and
 * a single query field would overwrite the first question with the second,
 * losing what was already answered and why.
 */
@Entity
@Table(name = "preauth_queries")
@Getter
@Setter
public class PreAuthQueryEntity extends AuditableEntity {

    @Column(name = "nhcx_transaction_id", nullable = false)
    private UUID nhcxTransactionId;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber = 1;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt = Instant.now();

    @Column(name = "query_code", length = 60)
    private String queryCode;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "responded_by")
    private UUID respondedBy;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    /** Comma-separated attachment ids; the documents live in `attachments`. */
    @Column(name = "response_attachments", columnDefinition = "TEXT")
    private String responseAttachments;
}
