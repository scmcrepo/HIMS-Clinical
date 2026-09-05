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
 * When one store's data stops being kept — DPDP s. 8(7).
 *
 * <p>Policy lives in a table rather than in code because a retention period is a
 * legal determination that differs per hospital and changes when the law does.
 * As constants, a lawyer's decision would become a deployment.
 *
 * <p>This is the complement to {@code ErasureService.TARGETS}, which stays in
 * code: that answers the <em>order and mechanism</em> of clearing a store, which
 * is a structural property of the schema. This answers <em>when</em>.
 *
 * <p>Both {@link #enabled} and {@link #dryRun} default to the safe setting. A
 * scheduled job that destroys patient records is the highest-consequence thing
 * in this codebase, and a hospital discovering its retention policy by watching
 * records disappear is a worse outcome than one that never switches the job on.
 */
@Entity
@Table(name = "retention_policies")
// Tenant-wide. branch_id exists on the table (V215) only because AuditableEntity
// maps it; this record belongs to the hospital, not to one of its locations, so
// branchFilter is disabled and the column stays NULL. Do not "tidy" the 1=1 away:
// re-enabling the branch filter hides compliance records from other branches.
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "1=1")
@Getter
@Setter
public class RetentionPolicyEntity extends AuditableEntity {

    @Column(name = "target_store", nullable = false, length = 60)
    private String targetStore;

    /**
     * The timestamp column age is measured from.
     *
     * <p>Validated against the live schema at startup rather than at 2am — a
     * misnamed column here means either a job that silently matches nothing or
     * one that throws in the middle of a sweep.
     */
    @Column(name = "date_column", nullable = false, length = 60)
    private String dateColumn;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    /** DELETE or ANONYMISE. */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /**
     * Which column {@code ANONYMISE} nulls.
     *
     * <p>Defaults to {@code patient_id}, but not every store links to a patient
     * by that name — {@code agent_tool_invocations} uses {@code target_entity_id}
     * because the same table logs tool calls against appointments and encounters
     * too. Ignored when the action is {@code DELETE}.
     */
    @Column(name = "anonymise_column", nullable = false, length = 60)
    private String anonymiseColumn = "patient_id";

    /**
     * Why this period. Mandatory: a retention period nobody can justify is one
     * nobody can defend to a regulator or to a patient asking why their record
     * is gone.
     */
    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    /** Set where a statute rather than a business decision fixes the period. */
    @Column(name = "statutory_basis", length = 200)
    private String statutoryBasis;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = true;

    /**
     * Ceiling on rows touched in one pass.
     *
     * <p>Guards against the shape of a misconfiguration — a wrong
     * {@code dateColumn} or a period entered in the wrong unit would otherwise
     * clear a large fraction of a table in a single run, at 2am, unattended.
     */
    @Column(name = "max_rows_per_run", nullable = false)
    private int maxRowsPerRun = 500;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_run_affected")
    private Integer lastRunAffected;

    /** Whether this policy will actually change data on the next run. */
    public boolean isLive() {
        return enabled && !dryRun;
    }
}
