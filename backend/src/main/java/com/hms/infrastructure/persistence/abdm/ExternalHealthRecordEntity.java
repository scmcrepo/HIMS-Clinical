package com.hms.infrastructure.persistence.abdm;

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
 * A record fetched from another provider — Screen 3.2.
 *
 * <p>This is clinical data the hospital did not author and does not own. The
 * payload is encrypted, and {@link #artifactId} records which consent let it in,
 * so a revoked grant can be traced to everything fetched under it.
 *
 * <p>{@link #displayTitle} exists so the viewer can render an index without
 * decrypting and parsing every bundle — a list of thirty records should not mean
 * thirty decryptions.
 */
@Entity
@Table(name = "external_health_records")
@Getter
@Setter
public class ExternalHealthRecordEntity extends AuditableEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Column(name = "hi_type", nullable = false, length = 40)
    private String hiType;

    /** When the care happened, not when it was fetched. */
    @Column(name = "record_date")
    private Instant recordDate;

    @Column(name = "source_hip_id", length = 80)
    private String sourceHipId;

    @Column(name = "source_hip_name", length = 200)
    private String sourceHipName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "display_title", length = 300)
    private String displayTitle;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    @Column(name = "imported_at")
    private Instant importedAt;

    @Column(name = "imported_by")
    private UUID importedBy;

    @Column(name = "imported_case_sheet_id")
    private UUID importedCaseSheetId;
}
