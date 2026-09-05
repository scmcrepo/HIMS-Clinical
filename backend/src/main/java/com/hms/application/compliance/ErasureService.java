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
import java.util.Set;

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
 * sweep rather than in documentation, and why
 * {@code ErasureRegistryCompletenessTest} reads the migration directory and
 * fails when a new {@code patient_id} column appears without a strategy.
 *
 * <h2>What WO-024 corrected</h2>
 *
 * <p>The registry previously listed six stores and its SQL had never been
 * executed. Against the real schema:
 * <ul>
 *   <li>{@code agent_idempotency_keys} had no {@code patient_id}, so the delete
 *       threw and the target was recorded FAILED — leaving cached tool responses
 *       in place.</li>
 *   <li>{@code hitl_escalations} had no {@code patient_id} either, and its
 *       anonymisation subquery matched <em>every run in the tenant</em>. One
 *       patient's erasure would have destroyed every other patient's transcript.</li>
 *   <li>Twenty-one tables carry {@code patient_id}; six were registered.</li>
 * </ul>
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

    enum Strategy { DELETE, ANONYMISE, RETAIN }

    /**
     * Every store holding patient-linked data, and how each is cleared.
     *
     * <p>Ordered from derived data to primary record, so a failure part-way
     * through leaves the primary intact and the request resumable rather than
     * leaving orphaned copies pointing at a deleted patient. {@code patients} is
     * deliberately last, and {@code consent_records} last of all.
     */
    static final Map<String, Strategy> TARGETS = new LinkedHashMap<>();

    static {
        // ── Derived / cached copies. Safe to delete outright. ──────────────
        TARGETS.put("agent_idempotency_keys",     Strategy.DELETE);
        TARGETS.put("portal_sessions",            Strategy.DELETE);
        TARGETS.put("discovered_policies",        Strategy.DELETE);
        TARGETS.put("patient_policy_coverages",   Strategy.DELETE);
        TARGETS.put("abha_linkages",              Strategy.DELETE);
        // U-005. A patient's mobile number and the text of messages about their
        // care. DELETE rather than RETAIN because the table has no reader, no
        // writer and no stated purpose, so there is nothing to weigh against the
        // erasure right.
        //
        // ORDERING IS LOAD-BEARING: this must run before "patients", which nulls
        // contact_number_token. That token is the only route from a patient to
        // these rows once to_number is encrypted; erase the patient first and
        // these rows become permanently unreachable.
        TARGETS.put("sms_logs",                   Strategy.DELETE);

        // Records pulled from other facilities under an ABDM consent artifact.
        // Deleted, not anonymised: the hospital was only ever a custodian of
        // these, and the artifact that authorised them has no bearing on
        // retention once the patient asks for erasure.
        TARGETS.put("external_health_records",    Strategy.DELETE);
        TARGETS.put("abdm_consent_artifacts",     Strategy.DELETE);
        TARGETS.put("abdm_consent_requests",      Strategy.DELETE);

        // ── Agent traces. Keep the run, lose the person. ───────────────────
        TARGETS.put("hitl_escalations",           Strategy.ANONYMISE);
        TARGETS.put("agent_tool_invocations",     Strategy.ANONYMISE);

        // ── Clinical. Retained under medico-legal obligation. ──────────────
        // Indian medical-record retention (typically 3 years for outpatient
        // records, longer where litigation is live or the patient was a minor)
        // overrides the erasure right. The patient must be told, which is what
        // ErasureRequestEntity.retainedReason carries.
        TARGETS.put("clinical_encounters",        Strategy.RETAIN);
        TARGETS.put("diagnostic_orders",          Strategy.RETAIN);
        TARGETS.put("visits",                     Strategy.RETAIN);
        TARGETS.put("attachments",                Strategy.RETAIN);
        TARGETS.put("patient_pediatric",          Strategy.RETAIN);
        // U-005. Clinical template content per encounter. RETAIN to match its
        // parent: deleting the template while keeping clinical_encounters would
        // leave a medical record with a hole in it, which is worse for the
        // patient than either extreme.
        TARGETS.put("template_data",              Strategy.RETAIN);

        // ── Financial. Anonymised: the money must reconcile, the person need
        //    not be named. Claims under adjudication cannot vanish mid-flight.
        TARGETS.put("nhcx_transactions",          Strategy.ANONYMISE);
        TARGETS.put("insurances",                 Strategy.ANONYMISE);
        TARGETS.put("bills",                      Strategy.ANONYMISE);
        TARGETS.put("payments",                   Strategy.ANONYMISE);
        TARGETS.put("pharmacy_sales",             Strategy.ANONYMISE);
        TARGETS.put("sales_returns",              Strategy.ANONYMISE);

        // ── Scheduling. No retention interest once care has ended. ─────────
        TARGETS.put("appointments",               Strategy.ANONYMISE);

        // ── The patient record itself. Anonymised rather than deleted so the
        //    retained clinical rows above do not become orphans pointing at a
        //    vanished id, which would break referential integrity and make the
        //    retained records unreadable — the opposite of what retention is
        //    for. Every encrypted identifier is nulled.
        TARGETS.put("patients",                   Strategy.ANONYMISE);

        // ── Records of the patient exercising their own rights. All retained.
        //
        //    Erasing these would destroy the evidence that a right was
        //    exercised and honoured — which is precisely what an inquiry would
        //    ask to see, and precisely what the patient would need if they
        //    later disputed how their request was handled. Retaining a
        //    complaint against the wishes of the person who made it is
        //    uncomfortable; destroying the only proof they complained is worse.
        TARGETS.put("grievances",                 Strategy.RETAIN);
        //    Ids only, and the record that a breach notification was owed and
        //    sent. See IncidentAffectedPrincipalEntity for why no contact
        //    details are stored here.
        TARGETS.put("incident_affected_principals", Strategy.RETAIN);

        // ── Last. Consent records are the evidence that consent existed and
        //    was withdrawn. Deleting them destroys the very audit trail the Act
        //    requires, so they are retained deliberately.
        TARGETS.put("consent_records",            Strategy.RETAIN);
    }

    /**
     * Stores whose rows written before V206 carry no {@code patient_id} and
     * cannot be attributed retrospectively.
     *
     * <p>Unattributable PHI that cannot be erased on request should not be
     * retained, so the first sweep in a tenant clears the pre-V206 backlog
     * wholesale rather than leaving it in place forever.
     */
    private static final Set<String> LEGACY_UNATTRIBUTABLE =
        Set.of("hitl_escalations", "agent_idempotency_keys");

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
     *
     * @throws IllegalStateException if the requester was never verified — a
     *         sweep on an unverified request destroys data on a stranger's
     *         say-so and denies the real patient their history
     */
    @Transactional
    public List<ErasureTargetEntity> sweep(ErasureRequestEntity request) {
        if (request.getRequesterVerifiedAt() == null) {
            throw new IllegalStateException(
                "Erasure cannot run before the requester is verified as the patient");
        }

        List<ErasureTargetEntity> results = new ArrayList<>();
        boolean anyFailed = false;
        List<String> retained = new ArrayList<>();

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
                    case RETAIN -> countRetained(table, request);
                };
                target.setRowsAffected(affected);
                target.setOutcome(switch (strategy) {
                    case DELETE -> "ERASED";
                    case ANONYMISE -> "ANONYMISED";
                    case RETAIN -> "RETAINED";
                });
                if (strategy == Strategy.RETAIN && affected > 0) {
                    target.setDetail(retentionReasonFor(table));
                    retained.add(table);
                }
            } catch (RuntimeException e) {
                anyFailed = true;
                target.setOutcome("FAILED");
                // The exception text can quote patient data; keep only the type.
                target.setDetail(e.getClass().getSimpleName());
                log.error("event=erasure.target.failed request_id={} store={} error_type={}",
                          request.getId(), table, e.getClass().getSimpleName());
            }

            entityManager.persist(target);
            results.add(target);
        }

        request.setState(anyFailed ? "PARTIALLY_COMPLETED" : "COMPLETED");
        request.setCompletedAt(Instant.now());
        request.setRetainedReason(retained.isEmpty()
            ? null
            : "Retained under statutory obligation and not erased: "
              + String.join(", ", retained)
              + ". Consent records are retained as the audit trail of consent "
              + "and its withdrawal. Financial records are anonymised rather "
              + "than deleted so accounts reconcile without naming you.");

        meterRegistry.counter("hms_erasure_requests_total",
                              "outcome", request.getState()).increment();
        log.info("event=erasure.completed request_id={} patient_id={} state={} targets={}",
                 request.getId(), request.getPatientId(), request.getState(), results.size());
        return results;
    }

    /**
     * Table names come from {@link #TARGETS}, never from user input, so the
     * dynamic SQL below cannot be injected into. Native queries are used because
     * these tables span several JPA modules and a per-entity approach would need
     * a repository dependency for each.
     *
     * <p>Every statement is tenant-scoped as well as patient-scoped. Patient ids
     * are UUIDs and collisions across tenants are not a realistic concern, but
     * the tenant predicate means a bug in id handling cannot reach another
     * hospital's rows — which is the failure this codebase guards hardest against.
     */
    private int deleteFrom(String table, ErasureRequestEntity request) {
        // U-005. sms_logs carries neither patient_id nor tenant_id — it predates
        // both conventions. The only link is the deterministic phone token, so
        // the patient is resolved through patients.contact_number_token and the
        // tenant predicate is applied there.
        //
        // A NULL token matches nothing, which is the right behaviour: a patient
        // with no phone number on file has no rows here, and a token-less sms_logs
        // row cannot be attributed to anyone. Those unattributable rows are a
        // separate problem, noted in V220 as an s. 8(7) question.
        //
        // KNOWN LIMITATION, verified against a real schema and stated rather than
        // hidden: sms_logs has no tenant_id, so this delete is tenant-blind at the
        // row level. The tenant predicate scopes which PATIENT is resolved, not
        // which ROWS are removed. If the same mobile number is registered at two
        // hospitals on this deployment — a family sharing a phone, or a patient
        // attending both — erasing at one hospital deletes the other's rows for
        // that number too.
        //
        // Accepted deliberately, because the alternatives are worse. Leaving the
        // rows keeps the s. 12 gap open, and the failure here is over-deletion of
        // legacy data that has no reader, no writer and no stated purpose. It
        // cannot disclose anything across tenants: the statement only deletes.
        //
        // It is still one tenant's action touching another's rows, which is the
        // thing this codebase guards hardest against, so it should not survive as
        // a permanent design. The clean fix is to decide this table's fate under
        // s. 8(7) — see the note in V220 — rather than to keep refining a
        // predicate over data nobody uses.
        if ("sms_logs".equals(table)) {
            return entityManager
                .createNativeQuery(
                    "DELETE FROM sms_logs s WHERE s.to_number_token IS NOT NULL "
                    + "AND s.to_number_token IN ("
                    + "  SELECT p.contact_number_token FROM patients p "
                    + "  WHERE p.id = :pid AND p.tenant_id = :tid "
                    + "    AND p.contact_number_token IS NOT NULL)")
                .setParameter("pid", request.getPatientId())
                .setParameter("tid", request.getTenantId())
                .executeUpdate();
        }

        int n = entityManager
            .createNativeQuery("DELETE FROM " + table
                               + " WHERE patient_id = :pid AND tenant_id = :tid")
            .setParameter("pid", request.getPatientId())
            .setParameter("tid", request.getTenantId())
            .executeUpdate();
        return n + sweepLegacyUnattributable(table, request);
    }

    private int anonymiseIn(String table, ErasureRequestEntity request) {
        String sql = switch (table) {
            // The transcript and the operator's reply are the personal parts;
            // the queue timings are not. Scoped by patient_id (added in V206) —
            // the previous subquery matched every run in the tenant.
            case "hitl_escalations" ->
                "UPDATE hitl_escalations SET transcript = NULL, operator_reply = NULL, "
                + "detail = NULL, patient_id = NULL "
                + "WHERE tenant_id = :tid AND patient_id = :pid";

            case "agent_tool_invocations" ->
                "UPDATE agent_tool_invocations SET target_entity_id = NULL "
                + "WHERE tenant_id = :tid AND target_entity_id = :pid";

            case "nhcx_transactions" ->
                "UPDATE nhcx_transactions SET patient_id = NULL, response_payload = NULL "
                + "WHERE tenant_id = :tid AND patient_id = :pid";

            // Financial rows keep their amounts and lose their subject.
            case "insurances", "bills", "payments", "pharmacy_sales", "sales_returns" ->
                "UPDATE " + table + " SET patient_id = NULL "
                + "WHERE tenant_id = :tid AND patient_id = :pid";

            case "appointments" ->
                "UPDATE appointments SET patient_id = NULL, notes = NULL "
                + "WHERE tenant_id = :tid AND patient_id = :pid";

            // The primary record. Every encrypted identifier is nulled; the row
            // survives so retained clinical records do not become orphans.
            case "patients" ->
                "UPDATE patients SET first_name = NULL, last_name = NULL, "
                + "contact_number = NULL, contact_number_token = NULL, email = NULL, "
                + "address = NULL, blood_group = NULL, date_of_birth = NULL, "
                + "pediatric_data = NULL, template_data = NULL, status = 0 "
                + "WHERE tenant_id = :tid AND id = :pid";

            default -> throw new IllegalStateException("No anonymisation defined for " + table);
        };

        var query = entityManager.createNativeQuery(sql)
                                 .setParameter("tid", request.getTenantId());
        if (sql.contains(":pid")) {
            query.setParameter("pid", request.getPatientId());
        }
        int n = query.executeUpdate();
        return n + sweepLegacyUnattributable(table, request);
    }

    /**
     * Clear PHI written before V206 that carries no {@code patient_id}.
     *
     * <p>These rows cannot be attributed to a patient by any join — the link was
     * never stored. Holding unattributable PHI that no erasure request can reach
     * is worse than clearing it, so the first sweep in a tenant clears the
     * backlog. Runs once per tenant because after it there is nothing left to
     * find.
     */
    private int sweepLegacyUnattributable(String table, ErasureRequestEntity request) {
        if (!LEGACY_UNATTRIBUTABLE.contains(table)) {
            return 0;
        }
        String sql = switch (table) {
            case "hitl_escalations" ->
                "UPDATE hitl_escalations SET transcript = NULL, operator_reply = NULL, detail = NULL "
                + "WHERE tenant_id = :tid AND patient_id IS NULL AND transcript IS NOT NULL";
            case "agent_idempotency_keys" ->
                "DELETE FROM agent_idempotency_keys "
                + "WHERE tenant_id = :tid AND patient_id IS NULL";
            default -> null;
        };
        if (sql == null) {
            return 0;
        }
        int n = entityManager.createNativeQuery(sql)
                             .setParameter("tid", request.getTenantId())
                             .executeUpdate();
        if (n > 0) {
            log.warn("event=erasure.legacy.swept store={} rows={} tenant_id={}",
                     table, n, request.getTenantId());
            meterRegistry.counter("hms_erasure_legacy_rows_total", "store", table).increment(n);
        }
        return n;
    }

    /**
     * How many rows are being kept, so the patient can be told the size of what
     * was retained rather than only that something was.
     */
    private int countRetained(String table, ErasureRequestEntity request) {
        // patient_pediatric (V010) is keyed on patient_id alone and carries no
        // tenant_id, so the generic predicate below throws
        // "column tenant_id does not exist" against it. Caught by sweep(), the
        // store was recorded FAILED on EVERY erasure request — a permanent
        // failure that made every receipt read PARTIALLY_COMPLETED and taught
        // whoever read it that a FAILED target is normal.
        //
        // Scoped through patients rather than by dropping the tenant predicate:
        // an unscoped count would report another hospital's rows if a patient id
        // were ever mishandled, and this is a count shown to a data principal.
        // Two tables predate the patient_id/tenant_id convention and need their
        // own predicate. Both are scoped through a table that does carry the
        // tenant, rather than by dropping the tenant check: this count is shown
        // to a data principal, and an unscoped one could report another
        // hospital's rows if a patient id were ever mishandled.
        String sql = switch (table) {
            case "patient_pediatric" ->
                "SELECT COUNT(*) FROM patient_pediatric pp "
                + "JOIN patients p ON p.id = pp.patient_id "
                + "WHERE pp.patient_id = :pid AND p.tenant_id = :tid";
            // U-005. Linked to a patient only through the encounter.
            case "template_data" ->
                "SELECT COUNT(*) FROM template_data td "
                + "JOIN clinical_encounters ce ON ce.id = td.encounter_id "
                + "WHERE ce.patient_id = :pid AND ce.tenant_id = :tid";
            default ->
                "SELECT COUNT(*) FROM " + table
                + " WHERE patient_id = :pid AND tenant_id = :tid";
        };

        Object result = entityManager
            .createNativeQuery(sql)
            .setParameter("pid", request.getPatientId())
            .setParameter("tid", request.getTenantId())
            .getSingleResult();
        return result == null ? 0 : ((Number) result).intValue();
    }

    private static String retentionReasonFor(String table) {
        return switch (table) {
            case "consent_records" ->
                "Retained as the audit record of consent and its withdrawal";
            case "grievances" ->
                "Retained as the record that you raised a complaint and how it was "
                + "resolved, so the account of it survives even if the rest is erased";
            case "incident_affected_principals" ->
                "Retained as the record that you were notified of a data breach";
            case "clinical_encounters", "diagnostic_orders", "visits",
                 "attachments", "patient_pediatric", "template_data" ->
                "Retained under medical-record retention obligations, which override "
                + "the erasure right for the duration of the statutory period";
            default -> "Retained under a statutory obligation";
        };
    }

    /** The registry, exposed so a test can assert every patient-data store is listed. */
    public static Set<String> registeredStores() {
        return java.util.Collections.unmodifiableSet(TARGETS.keySet());
    }
}
