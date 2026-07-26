package com.hms.application.compliance;

import com.hms.infrastructure.persistence.compliance.ErasureRequestEntity;
import com.hms.infrastructure.persistence.compliance.ErasureTargetEntity;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Processes a DPDP erasure request across every store that holds patient data.
 *
 * <p>The hard part of erasure is not deleting a row — it is knowing where the
 * copies are. Patient data does not stay in the patient table: it is duplicated
 * into agent idempotency caches, HITL transcripts, NHCX response payloads and
 * ABHA linkages. A sweep that clears the primary record and leaves those behind
 * has not complied; it has only made the remaining copies harder to find.
 *
 * <p>{@link #TARGETS} is therefore a registry that must be updated whenever a new
 * store starts holding patient data. If you add such a table and do not add it
 * here, erasure silently misses it — which is why the registry lives next to the
 * sweep rather than in documentation.
 *
 * <p>Three outcomes per store, and the distinction matters legally:
 * <ul>
 *   <li><b>ERASED</b> — rows deleted.</li>
 *   <li><b>ANONYMISED</b> — identifiers cleared but the row survives, because
 *       something non-personal still depends on it (audit counts, claim
 *       reconciliation).</li>
 *   <li><b>RETAINED</b> — kept under a statutory obligation that overrides the
 *       erasure right. The patient must be told this happened and why.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErasureService {

    /**
     * Every store holding patient-linked data, and how each is cleared.
     *
     * <p>Ordered from derived data to primary record, so a failure part-way
     * through leaves the primary intact and the request resumable rather than
     * leaving orphaned copies pointing at a deleted patient.
     */
    private static final Map<String, Strategy> TARGETS = new LinkedHashMap<>();

    private enum Strategy { DELETE, ANONYMISE, RETAIN }

    static {
        // Derived / cached copies — safe to delete outright.
        TARGETS.put("agent_idempotency_keys", Strategy.DELETE);
        TARGETS.put("hitl_escalations",       Strategy.ANONYMISE);
        TARGETS.put("abha_linkages",          Strategy.DELETE);

        // Agent audit keeps the run, loses the person: the counts and timings
        // remain meaningful without any link to a patient.
        TARGETS.put("agent_tool_invocations", Strategy.ANONYMISE);

        // Claims under adjudication cannot vanish mid-flight, and settled claims
        // carry financial retention. Identifiers go, the financial record stays.
        TARGETS.put("nhcx_transactions",      Strategy.ANONYMISE);

        // Consent records are the evidence that consent existed and was
        // withdrawn. Deleting them would destroy the very audit trail the Act
        // requires, so they are retained deliberately.
        TARGETS.put("consent_records",        Strategy.RETAIN);
    }

    @PersistenceContext
    private EntityManager entityManager;

    private final MeterRegistry meterRegistry;

    /**
     * Sweep every registered store.
     *
     * <p>Each target is recorded whatever happens, including on failure. A
     * request that half-succeeded and reports honestly is recoverable; one that
     * reports success it did not achieve is a compliance claim that will not
     * survive scrutiny.
     */
    @Transactional
    public List<ErasureTargetEntity> sweep(ErasureRequestEntity request) {
        List<ErasureTargetEntity> results = new ArrayList<>();
        boolean anyFailed = false;

        for (Map.Entry<String, Strategy> entry : TARGETS.entrySet()) {
            String table = entry.getKey();
            Strategy strategy = entry.getValue();

            ErasureTargetEntity target = new ErasureTargetEntity();
            target.setRequestId(request.getId());
            target.setTenantId(request.getTenantId());
            target.setTargetStore(table);
            target.setProcessedAt(Instant.now());

            try {
                int affected = switch (strategy) {
                    case DELETE -> deleteFrom(table, request);
                    case ANONYMISE -> anonymiseIn(table, request);
                    case RETAIN -> 0;
                };
                target.setRowsAffected(affected);
                target.setOutcome(switch (strategy) {
                    case DELETE -> "ERASED";
                    case ANONYMISE -> "ANONYMISED";
                    case RETAIN -> "RETAINED";
                });
                if (strategy == Strategy.RETAIN) {
                    target.setDetail("Retained as the audit record of consent and its withdrawal");
                }
            } catch (RuntimeException e) {
                anyFailed = true;
                target.setOutcome("FAILED");
                // The exception text can quote patient data; keep only the type.
                target.setDetail(e.getClass().getSimpleName());
                log.error("erasure.target.failed request[{}] store[{}] type[{}]",
                          request.getId(), table, e.getClass().getSimpleName());
            }

            entityManager.persist(target);
            results.add(target);
        }

        request.setState(anyFailed ? "PARTIALLY_COMPLETED" : "COMPLETED");
        request.setCompletedAt(Instant.now());
        if (!anyFailed) {
            request.setRetainedReason(
                "Consent records retained as the statutory audit trail. "
                + "Financial and claim records anonymised rather than deleted, "
                + "under financial retention obligations.");
        }

        meterRegistry.counter("hms_erasure_requests_total",
                              "outcome", request.getState()).increment();
        log.info("erasure.completed request[{}] patient[{}] state[{}] targets[{}]",
                 request.getId(), request.getPatientId(), request.getState(), results.size());
        return results;
    }

    /**
     * Table names come from {@link #TARGETS}, never from user input, so the
     * dynamic SQL below cannot be injected into. Native queries are used because
     * these tables span several JPA modules and a per-entity approach would need
     * a repository dependency for each.
     */
    private int deleteFrom(String table, ErasureRequestEntity request) {
        return entityManager
            .createNativeQuery("DELETE FROM " + table
                               + " WHERE patient_id = :pid AND tenant_id = :tid")
            .setParameter("pid", request.getPatientId())
            .setParameter("tid", request.getTenantId())
            .executeUpdate();
    }

    private int anonymiseIn(String table, ErasureRequestEntity request) {
        String sql = switch (table) {
            case "hitl_escalations" ->
                // The transcript is the personal part; the queue metrics are not.
                "UPDATE hitl_escalations SET transcript = NULL, operator_reply = NULL "
                + "WHERE tenant_id = :tid AND run_id IN "
                + "(SELECT run_id FROM hitl_escalations WHERE tenant_id = :tid)";
            case "agent_tool_invocations" ->
                "UPDATE agent_tool_invocations SET target_entity_id = NULL "
                + "WHERE tenant_id = :tid AND target_entity_id = :pid";
            case "nhcx_transactions" ->
                "UPDATE nhcx_transactions SET patient_id = NULL, response_payload = NULL "
                + "WHERE tenant_id = :tid AND patient_id = :pid";
            default -> throw new IllegalStateException("No anonymisation defined for " + table);
        };

        var query = entityManager.createNativeQuery(sql).setParameter("tid", request.getTenantId());
        if (sql.contains(":pid")) {
            query.setParameter("pid", request.getPatientId());
        }
        return query.executeUpdate();
    }

    /** The registry, exposed so a test can assert every patient-data store is listed. */
    public static java.util.Set<String> registeredStores() {
        return java.util.Collections.unmodifiableSet(TARGETS.keySet());
    }
}
