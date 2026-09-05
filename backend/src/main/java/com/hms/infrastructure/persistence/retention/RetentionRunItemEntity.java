package com.hms.infrastructure.persistence.retention;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/** What one policy did in one run. */
@Entity
@Table(name = "retention_run_items")
// Tenant-wide. branch_id exists on the table (V215) only because AuditableEntity
// maps it; this record belongs to the hospital, not to one of its locations, so
// branchFilter is disabled and the column stays NULL. Do not "tidy" the 1=1 away:
// re-enabling the branch filter hides compliance records from other branches.
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "1=1")
@Getter
@Setter
public class RetentionRunItemEntity extends AuditableEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "target_store", nullable = false, length = 60)
    private String targetStore;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "cutoff_at", nullable = false)
    private Instant cutoffAt;

    @Column(name = "rows_matched", nullable = false)
    private int rowsMatched;

    /**
     * Differs from {@link #rowsMatched} in a dry run, where nothing is touched,
     * and when {@code maxRowsPerRun} caps the pass.
     */
    @Column(name = "rows_affected", nullable = false)
    private int rowsAffected;

    @Column(name = "capped", nullable = false)
    private boolean capped;

    /** DRY_RUN | APPLIED | SKIPPED | FAILED | CAPPED */
    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "detail", length = 300)
    private String detail;
}
