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
 * One row per release of unmasked personal data.
 *
 * <p>Records <b>that</b> a disclosure happened — never the disclosed value. An
 * audit log holding the identifiers it exists to protect is just a second copy
 * of the problem, and one that tends to have weaker access controls than the
 * table it was guarding.
 */
@Entity
@Table(name = "pii_disclosure_audit")
@Getter
@Setter
public class PiiDisclosureAuditEntity extends AuditableEntity {

    /** ABHA_CARD | EXTERNAL_HEALTH_RECORD | POLICY_DOCUMENT */
    @Column(name = "disclosure_type", nullable = false, length = 40)
    private String disclosureType;

    /** Surrogate key of the data subject. Never a name or an identifier value. */
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    /** SUCCESS | DENIED | FAILURE */
    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome = "SUCCESS";

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "disclosed_at", nullable = false)
    private Instant disclosedAt = Instant.now();
}
