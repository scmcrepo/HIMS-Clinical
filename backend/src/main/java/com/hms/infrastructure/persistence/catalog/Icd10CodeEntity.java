package com.hms.infrastructure.persistence.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One ICD-10 diagnosis code.
 *
 * <p>Deliberately not an {@code AuditableEntity}. This is a published reference
 * dataset from WHO, localised by MoHFW — the hospital does not author it, does
 * not own it, and "who created this row" is answered by "the WHO release", not
 * by a user. {@code tenant_id} is nullable for the same reason: the catalogue is
 * shared, and a per-tenant copy of 70,000 codes would be 70,000 rows of
 * duplication with no tenant-specific meaning.
 */
@Entity
@Table(name = "icd10_codes")
@Getter
@Setter
public class Icd10CodeEntity {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    /** Null for the shared catalogue; set only for a tenant's local additions. */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "chapter", length = 120)
    private String chapter;

    /**
     * Whether the code may be used on a new claim.
     *
     * <p>Some ICD-10 codes are valid for historical records but not billable —
     * category headers, for instance. They are kept rather than dropped so old
     * records still resolve, and excluded from search so nobody puts one on a
     * pre-auth that the payer will reject.
     */
    @Column(name = "billable", nullable = false)
    private boolean billable = true;

    @Column(name = "status", nullable = false)
    private Short status = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt = Instant.now();
}
