package com.hms.infrastructure.persistence.retention;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * One execution of the retention job.
 *
 * <p>A retention job that deletes without a record is indistinguishable from
 * data loss. This is the evidence that a deletion was policy rather than an
 * incident, and it is the first thing anyone asks for when a record turns out to
 * be missing.
 */
@Entity
@Table(name = "retention_runs")
// Tenant-wide. branch_id exists on the table (V215) only because AuditableEntity
// maps it; this record belongs to the hospital, not to one of its locations, so
// branchFilter is disabled and the column stays NULL. Do not "tidy" the 1=1 away:
// re-enabling the branch filter hides compliance records from other branches.
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "1=1")
@Getter
@Setter
public class RetentionRunEntity extends AuditableEntity {

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    /** RUNNING | COMPLETED | FAILED | ABORTED */
    @Column(name = "state", nullable = false, length = 20)
    private String state = "RUNNING";

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "policies_evaluated", nullable = false)
    private int policiesEvaluated;

    @Column(name = "rows_affected", nullable = false)
    private int rowsAffected;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;
}
