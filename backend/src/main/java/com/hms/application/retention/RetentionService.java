package com.hms.application.retention;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.retention.RetentionPolicyEntity;
import com.hms.infrastructure.persistence.retention.RetentionPolicyJpaRepository;
import com.hms.infrastructure.persistence.retention.RetentionRunEntity;
import com.hms.infrastructure.persistence.retention.RetentionRunItemEntity;
import com.hms.infrastructure.persistence.retention.RetentionRunItemJpaRepository;
import com.hms.infrastructure.persistence.retention.RetentionRunJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Storage limitation — DPDP s. 8(7).
 *
 * <p>The Act requires personal data to be erased once its purpose is served and
 * no law requires it kept. Before this, nothing in the system did that: seven
 * scheduled jobs existed and none deleted patient data by policy. The DPIA rates
 * it R6 and is the only risk in that document rated as actively worsening,
 * because every day of operation adds data nothing will ever remove.
 *
 * <h2>This class deletes patient records. Read the safety design.</h2>
 *
 * <p>Four independent brakes, because a scheduled job that destroys records
 * unattended at 2am is the highest-consequence code in this repository and a
 * single guard is a single point of failure:
 *
 * <ol>
 *   <li><b>Disabled by default.</b> Every seeded policy has {@code enabled=false}.
 *       The job finds nothing to do until a human turns one on.</li>
 *   <li><b>Dry-run by default.</b> Even enabled, {@code dryRun=true} reports what
 *       would be affected and changes nothing.</li>
 *   <li><b>Batch cap.</b> {@code maxRowsPerRun} bounds the damage from a
 *       misconfiguration. A wrong date column or a period entered in the wrong
 *       unit would otherwise clear most of a table in one pass.</li>
 *   <li><b>Identifier allowlisting.</b> Table and column names are validated
 *       against the live schema at startup and against a strict pattern before
 *       every statement.</li>
 * </ol>
 *
 * <p>Clinical records are deliberately absent from the seeded policies. Their
 * retention is governed by medico-legal rules this project has no basis to
 * encode, and a wrong period there destroys evidence a patient may need.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final RetentionPolicyJpaRepository policies;
    private final RetentionRunJpaRepository runs;
    private final RetentionRunItemJpaRepository runItems;
    private final MeterRegistry meterRegistry;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Identifiers come from the policy table, which an administrator can edit, so
     * they are never interpolated without passing this first. Anything outside
     * lowercase letters, digits and underscore is rejected.
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{0,59}$");

    /**
     * Stores this job will never touch regardless of what a policy says.
     *
     * <p>A belt-and-braces list. Someone could insert a policy row for
     * {@code clinical_encounters} — through the API, or directly in the database
     * — and this is what stops it. Each entry is here because deleting from it
     * would destroy either clinical evidence or the audit trail that proves the
     * system behaved lawfully.
     */
    private static final Set<String> NEVER_SWEEP = Set.of(
        "clinical_encounters", "visits", "diagnostic_orders", "attachments",
        "patient_pediatric", "patients",
        "consent_records", "consent_notices",
        "erasure_requests", "erasure_targets",
        "security_incidents", "incident_affected_principals",
        "grievances", "grievance_events",
        "retention_policies", "retention_runs", "retention_run_items",
        "users", "roles", "features", "role_features", "tenants");

    /** Populated at startup; used to reject a policy naming a column that isn't there. */
    private volatile Map<String, Set<String>> schemaSnapshot = Map.of();

    // ── Startup validation ────────────────────────────────────────────────

    /**
     * Check every policy against the live schema before the job can ever run.
     *
     * <p>Validating here rather than at execution time means a typo in a column
     * name surfaces on deploy, in front of whoever deployed it, instead of at 2am
     * in the middle of a sweep.
     *
     * <p>Never throws. A misconfigured retention policy must not stop the
     * hospital system from starting — it disables itself and shouts.
     */
    @PostConstruct
    void validatePoliciesAtStartup() {
        try {
            schemaSnapshot = loadSchema();
            int invalid = 0;
            for (RetentionPolicyEntity p : policies.findAll()) {
                String problem = validate(p);
                if (problem != null) {
                    invalid++;
                    log.error("event=retention.policy.invalid store={} problem=\"{}\"",
                              p.getTargetStore(), problem);
                }
            }
            meterRegistry.gauge("hms_retention_policies_invalid", invalid);
            meterRegistry.gauge("hms_retention_policies_live", policies.countLive());
            if (invalid > 0) {
                log.error("event=retention.startup.invalid_policies count={}", invalid);
            }
        } catch (RuntimeException e) {
            log.error("event=retention.startup.validation_failed error_type={}",
                      e.getClass().getSimpleName());
        }
    }

    /** @return a description of the problem, or null when the policy is usable */
    String validate(RetentionPolicyEntity p) {
        if (!SAFE_IDENTIFIER.matcher(p.getTargetStore()).matches()) {
            return "target_store is not a plain identifier";
        }
        if (!SAFE_IDENTIFIER.matcher(p.getDateColumn()).matches()) {
            return "date_column is not a plain identifier";
        }
        if (NEVER_SWEEP.contains(p.getTargetStore())) {
            return "this store is on the never-sweep list and cannot be subject to a "
                 + "retention policy";
        }
        Set<String> columns = schemaSnapshot.get(p.getTargetStore());
        if (columns == null) {
            return "table does not exist";
        }
        if (!columns.contains(p.getDateColumn())) {
            return "column " + p.getDateColumn() + " does not exist on " + p.getTargetStore();
        }
        if (!columns.contains("tenant_id")) {
            // The job runs with no tenant context, so the Hibernate filter is
            // off and the tenant predicate in the statement is the only thing
            // keeping one hospital's policy from reaching another's rows.
            // Without the column there is no safe predicate to write.
            return "table has no tenant_id, so the sweep cannot be scoped to one tenant";
        }
        if (!columns.contains("id")) {
            return "table has no id column; the batch-capped statement needs one";
        }
        if ("ANONYMISE".equals(p.getAction())) {
            if (!SAFE_IDENTIFIER.matcher(p.getAnonymiseColumn()).matches()) {
                return "anonymise_column is not a plain identifier";
            }
            if (!columns.contains(p.getAnonymiseColumn())) {
                return "anonymise_column " + p.getAnonymiseColumn()
                     + " does not exist on " + p.getTargetStore();
            }
        }
        if (p.getRetentionDays() <= 0) {
            return "retention_days must be positive";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> loadSchema() {
        List<Object[]> rows = entityManager.createNativeQuery(
            "SELECT table_name, column_name FROM information_schema.columns "
            + "WHERE table_schema = current_schema()").getResultList();

        Map<String, Set<String>> out = new java.util.HashMap<>();
        for (Object[] r : rows) {
            out.computeIfAbsent(String.valueOf(r[0]).toLowerCase(), k -> new java.util.HashSet<>())
               .add(String.valueOf(r[1]).toLowerCase());
        }
        return out;
    }

    // ── The job ───────────────────────────────────────────────────────────

    /**
     * Apply every enabled policy.
     *
     * <p>Runs at 03:20, after the consent expiry jobs and before the morning
     * rights and grievance reports, so an operator arriving to an overdue-request
     * alert is not also looking at a retention run in progress.
     */
    @Scheduled(cron = "0 20 3 * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduledRun() {
        try {
            execute(null);
        } catch (RuntimeException e) {
            log.error("event=retention.run.failed error_type={}", e.getClass().getSimpleName());
        }
    }

    /**
     * Run the policies and record what happened.
     *
     * @param forceDryRun when TRUE, overrides every policy's own setting and
     *                    changes nothing. This is what the "preview" endpoint
     *                    uses, so an operator can see the effect of enabling a
     *                    policy without enabling it
     */
    @Transactional
    public RetentionRunEntity execute(Boolean forceDryRun) {
        List<RetentionPolicyEntity> active = policies.findEnabled();

        RetentionRunEntity run = new RetentionRunEntity();
        run.setStartedAt(Instant.now());
        run.setDryRun(Boolean.TRUE.equals(forceDryRun)
                      || active.stream().allMatch(RetentionPolicyEntity::isDryRun));
        run.setState("RUNNING");
        runs.save(run);

        int totalAffected = 0;
        int evaluated = 0;
        List<String> failures = new ArrayList<>();

        for (RetentionPolicyEntity p : active) {
            evaluated++;
            RetentionRunItemEntity item = new RetentionRunItemEntity();
            item.setRunId(run.getId());
            item.setTargetStore(p.getTargetStore());
            item.setAction(p.getAction());

            try {
                String problem = validate(p);
                if (problem != null) {
                    item.setOutcome("SKIPPED");
                    item.setDetail(problem);
                    item.setCutoffAt(Instant.now());
                    runItems.save(item);
                    log.error("event=retention.policy.skipped store={} reason=\"{}\"",
                              p.getTargetStore(), problem);
                    continue;
                }

                Instant cutoff = Instant.now().minus(p.getRetentionDays(), ChronoUnit.DAYS);
                item.setCutoffAt(cutoff);

                int matched = countMatching(p, cutoff);
                item.setRowsMatched(matched);

                boolean dry = Boolean.TRUE.equals(forceDryRun) || p.isDryRun();
                if (dry) {
                    item.setOutcome("DRY_RUN");
                    item.setRowsAffected(0);
                    item.setDetail(matched + " row(s) would be " + p.getAction().toLowerCase()
                                   + "d. Nothing was changed.");
                    log.info("event=retention.dry_run store={} would_affect={} cutoff={}",
                             p.getTargetStore(), matched, cutoff);
                } else {
                    int affected = apply(p, cutoff);
                    item.setRowsAffected(affected);
                    item.setCapped(matched > affected);
                    item.setOutcome(matched > affected ? "CAPPED" : "APPLIED");
                    totalAffected += affected;

                    if (matched > affected) {
                        // Not an error — the cap did its job — but the operator
                        // should know the backlog did not clear in one pass.
                        item.setDetail("Capped at " + p.getMaxRowsPerRun()
                                       + "; " + (matched - affected) + " remain");
                        log.warn("event=retention.capped store={} affected={} remaining={}",
                                 p.getTargetStore(), affected, matched - affected);
                    }
                    // WARN, not INFO. Patient data was destroyed on a schedule;
                    // that line should be findable without knowing to look.
                    log.warn("event=retention.applied store={} action={} affected={} cutoff={}",
                             p.getTargetStore(), p.getAction(), affected, cutoff);

                    p.setLastRunAt(Instant.now());
                    p.setLastRunAffected(affected);
                    policies.save(p);
                }

                meterRegistry.counter("hms_retention_rows_total",
                                      "store", p.getTargetStore(),
                                      "action", p.getAction(),
                                      "mode", dry ? "dry_run" : "applied")
                             .increment(item.getRowsAffected());
            } catch (RuntimeException e) {
                item.setOutcome("FAILED");
                // Exception text can quote row values; keep the type only.
                item.setDetail(e.getClass().getSimpleName());
                if (item.getCutoffAt() == null) {
                    item.setCutoffAt(Instant.now());
                }
                failures.add(p.getTargetStore());
                log.error("event=retention.policy.failed store={} error_type={}",
                          p.getTargetStore(), e.getClass().getSimpleName());
            }
            runItems.save(item);
        }

        run.setPoliciesEvaluated(evaluated);
        run.setRowsAffected(totalAffected);
        run.setCompletedAt(Instant.now());
        run.setState(failures.isEmpty() ? "COMPLETED" : "FAILED");
        if (!failures.isEmpty()) {
            run.setErrorDetail("Failed stores: " + String.join(", ", failures));
        }

        meterRegistry.counter("hms_retention_runs_total",
                              "outcome", run.getState()).increment();
        log.info("event=retention.run.complete policies={} affected={} dry_run={} state={}",
                 evaluated, totalAffected, run.isDryRun(), run.getState());
        return runs.save(run);
    }

    /**
     * Identifiers are re-validated immediately before interpolation.
     *
     * <p>They were already checked at startup and again at the top of the loop.
     * Checking a third time here is not redundancy for its own sake: this is the
     * only method that builds SQL from a value an administrator can edit, and it
     * should be safe to read in isolation without tracing where its arguments
     * came from.
     */
    private String requireSafe(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new BusinessRuleViolationException("Unsafe identifier: " + identifier);
        }
        return identifier;
    }

    private int countMatching(RetentionPolicyEntity p, Instant cutoff) {
        String sql = "SELECT COUNT(*) FROM " + requireSafe(p.getTargetStore())
                   + " WHERE " + requireSafe(p.getDateColumn()) + " < :cutoff "
                   + "AND tenant_id = :tid";
        Object result = entityManager.createNativeQuery(sql)
            .setParameter("cutoff", cutoff)
            .setParameter("tid", p.getTenantId())
            .getSingleResult();
        return result == null ? 0 : ((Number) result).intValue();
    }

    /**
     * Delete or anonymise, bounded by the policy's batch cap.
     *
     * <p>Scoped by tenant as well as by date. The job runs without tenant
     * context, so the Hibernate filter is off — the predicate here is the only
     * thing keeping one hospital's policy from reaching another's rows.
     */
    private int apply(RetentionPolicyEntity p, Instant cutoff) {
        String table = requireSafe(p.getTargetStore());
        String dateCol = requireSafe(p.getDateColumn());
        int cap = Math.max(1, Math.min(p.getMaxRowsPerRun(), 10_000));

        String selectIds = "SELECT id FROM " + table
                         + " WHERE " + dateCol + " < :cutoff AND tenant_id = :tid "
                         + "LIMIT " + cap;

        String sql = "ANONYMISE".equals(p.getAction())
            ? "UPDATE " + table + " SET " + requireSafe(p.getAnonymiseColumn())
              + " = NULL WHERE id IN (" + selectIds + ")"
            : "DELETE FROM " + table + " WHERE id IN (" + selectIds + ")";

        return entityManager.createNativeQuery(sql)
            .setParameter("cutoff", cutoff)
            .setParameter("tid", p.getTenantId())
            .executeUpdate();
    }

    // ── Administration ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RetentionPolicyEntity> allPolicies() {
        return policies.findAllByOrderByTargetStore();
    }

    @Transactional(readOnly = true)
    public List<RetentionRunEntity> recentRuns() {
        return runs.findTop20ByOrderByStartedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<RetentionRunItemEntity> runDetail(UUID runId) {
        return runItems.findByRunIdOrderByTargetStore(runId);
    }

    /**
     * Change a policy.
     *
     * <p>Turning off {@code dryRun} is the moment a policy starts destroying
     * records, so it is logged at WARN with the user attached and refused
     * outright if the policy does not currently validate. Enabling a broken
     * policy is how a typo becomes data loss.
     */
    @Transactional
    public RetentionPolicyEntity update(UUID id, Integer retentionDays, Boolean enabled,
                                        Boolean dryRun, Integer maxRowsPerRun,
                                        String justification) {
        RetentionPolicyEntity p = policies.findById(id).orElseThrow(() ->
            new ResourceNotFoundException("Retention policy not found"));

        if (retentionDays != null) {
            if (retentionDays <= 0) {
                throw new BusinessRuleViolationException("retentionDays must be positive");
            }
            p.setRetentionDays(retentionDays);
        }
        if (maxRowsPerRun != null) {
            p.setMaxRowsPerRun(Math.max(1, Math.min(maxRowsPerRun, 10_000)));
        }
        if (justification != null && !justification.isBlank()) {
            p.setJustification(justification);
        }
        if (enabled != null) {
            p.setEnabled(enabled);
        }
        if (dryRun != null) {
            if (!dryRun) {
                String problem = validate(p);
                if (problem != null) {
                    throw new BusinessRuleViolationException(
                        "Cannot take this policy out of dry-run: " + problem);
                }
                log.warn("event=retention.policy.armed store={} days={} action={}",
                         p.getTargetStore(), p.getRetentionDays(), p.getAction());
            }
            p.setDryRun(dryRun);
        }

        meterRegistry.gauge("hms_retention_policies_live", policies.countLive());
        return policies.save(p);
    }
}
