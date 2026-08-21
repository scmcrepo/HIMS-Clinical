package com.hms.application.report.modules;

import com.hms.application.report.util.ReportDbUtil;
import com.hms.application.report.util.ReportScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The ten insurance MIS reports (WO-021 / IR-001).
 *
 * <h2>Tenant scoping is the whole ballgame here</h2>
 * These run through {@link JdbcTemplate} and therefore bypass the Hibernate
 * {@code tenantFilter} entirely. <b>Every query below appends
 * {@code scope.predicate(alias)} and {@code scope.args()}</b>. A query that
 * forgets to is not a slow report — it is one hospital reading another
 * hospital's claim values, and it will look completely normal in review.
 *
 * <p>Follow the documented ordering when editing: the scope predicate goes at
 * the END of the WHERE conditions and {@code args.addAll(scope.args())} at the
 * END of the arg list, with any trailing clauses (ORDER BY, GROUP BY) appended
 * after. Positional '?' alignment is silent when it breaks.
 *
 * <h2>Encrypted columns</h2>
 * {@code claim_no}, the three reason fields, {@code policy_number} and patient
 * names come back as ciphertext. {@code BaseReportService.decryptQueryResult}
 * walks every string value on the way out, so the queries select them raw and
 * do not attempt any SQL-side handling.
 *
 * <h2>Amounts</h2>
 * Stored in paise; divided by 100.0 here so the rendered reports read in rupees,
 * matching every other report module.
 */
@Service
@RequiredArgsConstructor
public class InsuranceReportDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ReportScope scope;

    /**
     * Patient identity, joined the same way in every report so a name renders
     * identically across all ten.
     */
    private static final String PATIENT_COLS = """
            COALESCE(ns.value, '-')         AS "patient_no",
            TRIM(COALESCE(p.salutation || ' ', '') || COALESCE(p.first_name, '') || ' ' || COALESCE(p.last_name, '')) AS "patient",
        """;

    private static final String PATIENT_JOIN = """
        LEFT JOIN patients p         ON p.id = i.patient_id
        LEFT JOIN number_sequences ns ON ns.id = i.patient_id
        """;

    // ────────────────────────────────────────────────────────────────────────
    // 1. PRE-AUTHORISATION RAISED
    //    Every initial request sent in the window, with what was asked for.
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getPreAuthRaised(String fromDate, String toDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                              AS "insurer",
                COALESCE(i.tpa_name, '-')                   AS "tpa",
                i.policy_number                             AS "policy_no",
                i.pre_auth_type                             AS "preauth_type",
                i.preauth_applied_date                      AS "applied_on",
                COALESCE(i.preauth_requested_amount, 0) / 100.0 AS "requested_amount",
                CASE i.preauth_communication_to_tpa
                    WHEN 'FAX'  THEN 'Fax'
                    WHEN 'MAIL' THEN 'Mail'
                    ELSE '-' END                            AS "sent_via",
                COALESCE(i.insurance_current_status, 'LEGACY') AS "stage"
            FROM insurances i
        """ + PATIENT_JOIN + """
            WHERE i.preauth_applied_date >= ?::DATE
              AND i.preauth_applied_date < (?::DATE + INTERVAL '1 day')
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ORDER BY i.preauth_applied_date DESC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. PRE-AUTHORISATION STATUS
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getPreAuthStatus(String fromDate, String toDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                              AS "insurer",
                COALESCE(i.tpa_name, '-')                   AS "tpa",
                i.claim_no                                  AS "claim_no",
                i.preauth_applied_date                      AS "applied_on",
                i.preauth_date_of_approval                  AS "decided_on",
                COALESCE(i.preauth_requested_amount, 0) / 100.0 AS "requested_amount",
                COALESCE(i.preauth_approved_limit, 0) / 100.0   AS "approved_amount",
                i.preauth_rejection_reason                  AS "rejection_reason",
                CASE
                    WHEN i.preauth_approval_status = 'APPROVED' THEN 'Approved'
                    WHEN i.preauth_approval_status = 'REJECTED' THEN 'Rejected'
                    ELSE 'In process'
                END                                         AS "status"
            FROM insurances i
        """ + PATIENT_JOIN + """
            WHERE i.preauth_applied_date >= ?::DATE
              AND i.preauth_applied_date < (?::DATE + INTERVAL '1 day')
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ORDER BY i.preauth_applied_date DESC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. ENHANCEMENT RAISED
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getEnhancementRaised(String fromDate, String toDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                              AS "insurer",
                COALESCE(i.tpa_name, '-')                   AS "tpa",
                i.claim_no                                  AS "claim_no",
                i.enhancement_applied_date                  AS "applied_on",
                COALESCE(i.preauth_approved_limit, 0) / 100.0        AS "original_limit",
                COALESCE(i.enhancement_requested_amount, 0) / 100.0  AS "requested_amount",
                i.reason_for_enhancement                    AS "reason",
                CASE i.enhancement_communication_to_tpa
                    WHEN 'FAX'  THEN 'Fax'
                    WHEN 'MAIL' THEN 'Mail'
                    ELSE '-' END                            AS "sent_via"
            FROM insurances i
        """ + PATIENT_JOIN + """
            WHERE i.enhancement_applied_date >= ?::DATE
              AND i.enhancement_applied_date < (?::DATE + INTERVAL '1 day')
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ORDER BY i.enhancement_applied_date DESC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. ENHANCEMENT STATUS
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getEnhancementStatus(String fromDate, String toDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                              AS "insurer",
                COALESCE(i.tpa_name, '-')                   AS "tpa",
                i.claim_no                                  AS "claim_no",
                i.enhancement_applied_date                  AS "applied_on",
                i.enhancement_date_of_approval              AS "decided_on",
                COALESCE(i.enhancement_requested_amount, 0) / 100.0 AS "requested_amount",
                COALESCE(i.enhancement_approved_limit, 0) / 100.0   AS "approved_amount",
                i.enhancement_rejection_reason              AS "rejection_reason",
                CASE
                    WHEN i.enhancement_approval_status = 'APPROVED' THEN 'Approved'
                    WHEN i.enhancement_approval_status = 'REJECTED' THEN 'Rejected'
                    ELSE 'In process'
                END                                         AS "status"
            FROM insurances i
        """ + PATIENT_JOIN + """
            WHERE i.enhancement_applied_date >= ?::DATE
              AND i.enhancement_applied_date < (?::DATE + INTERVAL '1 day')
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ORDER BY i.enhancement_applied_date DESC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 5. CLAIM DISPATCH
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getClaimDispatch(String fromDate, String toDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                              AS "insurer",
                COALESCE(i.tpa_name, '-')                   AS "tpa",
                i.claim_no                                  AS "claim_no",
                i.dispatch_date                             AS "dispatched_on",
                COALESCE(i.mode_of_dispatch, '-')           AS "mode",
                COALESCE(i.courier, '-')                    AS "courier",
                COALESCE(i.pod_no, '-')                     AS "pod_no",
                COALESCE(i.dispatched_by, '-')              AS "dispatched_by",
                COALESCE(i.dispatch_mail_id, '-')           AS "sent_to",
                COALESCE(i.reason_for_delay, '')            AS "delay_reason",
                CASE WHEN i.preauth_date_of_approval IS NOT NULL
                     THEN DATE_PART('day', i.dispatch_date - i.preauth_date_of_approval)
                     ELSE NULL END                          AS "days_to_dispatch"
            FROM insurances i
        """ + PATIENT_JOIN + """
            WHERE i.dispatch_date >= ?::DATE
              AND i.dispatch_date < (?::DATE + INTERVAL '1 day')
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ORDER BY i.dispatch_date DESC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 6. DISALLOWANCE SUMMARY
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getDisallowanceSummary(String fromDate, String toDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                                     AS "insurer",
                COALESCE(i.tpa_name, '-')                          AS "tpa",
                COALESCE(SUM(cli.billed), 0) / 100.0               AS "billed_amount",
                COALESCE(SUM(cli.disallowed), 0) / 100.0           AS "disallowed_amount",
                COALESCE(SUM(chq.received), 0) / 100.0             AS "received_amount",
                CASE WHEN COALESCE(SUM(cli.billed), 0) = 0 THEN 0
                     ELSE ROUND(100.0 * COALESCE(SUM(cli.disallowed), 0)
                                / SUM(cli.billed), 2)
                END                                                AS "disallowed_pct"
            FROM insurances i
        """ + PATIENT_JOIN + """
            LEFT JOIN LATERAL (
                SELECT SUM(c.amount - c.discount_amount) AS billed,
                       SUM(c.disallowed_amount)          AS disallowed
                FROM charge_line_items c
                WHERE c.bill_id = i.bill_id
                  AND c.status = 1
            ) cli ON TRUE
            LEFT JOIN LATERAL (
                SELECT SUM(r.amount) AS received
                FROM insurance_cheque_receipts r
                WHERE r.insurance_id = i.id
                  AND r.status = 1
            ) chq ON TRUE
            WHERE i.created_at >= ?::DATE
              AND i.created_at < (?::DATE + INTERVAL '1 day')
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" GROUP BY ns.value, p.salutation, p.first_name, p.last_name, i.insurer_name, i.tpa_name ORDER BY COALESCE(SUM(cli.billed), 0) DESC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 7. DISALLOWANCE DETAIL
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getDisallowanceDetail(String fromDate, String toDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                              AS "insurer",
                i.claim_no                                  AS "claim_no",
                COALESCE(b.bill_number, '-')                AS "bill_no",
                b.bill_date                                 AS "bill_date",
                COALESCE(c.item_name, 'Other')              AS "charge",
                (c.amount - c.discount_amount) / 100.0      AS "billed_amount",
                c.disallowed_amount / 100.0                 AS "disallowed_amount",
                (c.amount - c.discount_amount - c.disallowed_amount) / 100.0 AS "net_payable"
            FROM insurances i
            JOIN bills b             ON b.id = i.bill_id
            JOIN charge_line_items c ON c.bill_id = b.id AND c.status = 1
        """ + PATIENT_JOIN + """
            WHERE c.disallowed_amount > 0
              AND b.bill_date >= ?::DATE
              AND b.bill_date <= ?::DATE
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ORDER BY b.bill_date DESC, c.disallowed_amount DESC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 8. DOCUMENT PENDING STATUS
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getDocumentPendingStatus(String fromDate, String toDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                              AS "insurer",
                COALESCE(i.tpa_name, '-')                   AS "tpa",
                i.claim_no                                  AS "claim_no",
                COALESCE(i.insurance_current_status, 'LEGACY') AS "stage",
                i.preauth_date_of_approval                  AS "sanctioned_on",
                COALESCE(i.check_list_updated_date, i.check_list_created_date) AS "checklist_updated",
                DATE_PART('day', NOW() - COALESCE(i.preauth_date_of_approval, i.created_at))
                                                            AS "days_pending",
                COALESCE(pend.shortfall_items, 0)           AS "shortfall_items",
                COALESCE(pend.pending_docs, '')             AS "pending_documents"
            FROM insurances i
            LEFT JOIN LATERAL (
                SELECT COUNT(*) AS shortfall_items,
                       STRING_AGG(item ->> 'name', ', ') AS pending_docs
                FROM jsonb_array_elements(
                        COALESCE(i.checklist -> 'checklists', '[]'::jsonb)) AS item
                WHERE COALESCE((item ->> 'submitted')::int, 0)
                      < COALESCE((item ->> 'toBeSubmit')::int, 0)
            ) pend ON TRUE
        """ + PATIENT_JOIN + """
            WHERE i.created_at >= ?::DATE
              AND i.created_at < (?::DATE + INTERVAL '1 day')
              AND i.dispatch_date IS NULL
              AND (
                    COALESCE(pend.shortfall_items, 0) > 0
                 OR (i.preauth_approval_status = 'APPROVED'
                     AND i.check_list_created_date IS NULL)
              )
            """);
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ORDER BY DATE_PART('day', NOW() - COALESCE(i.preauth_date_of_approval, i.created_at)) DESC NULLS LAST ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 9. IP OUTSTANDING CREDIT BILLS
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getOutstandingCreditBills(String asOnDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT
        """ + PATIENT_COLS + """
                i.insurer_name                              AS "insurer",
                COALESCE(i.tpa_name, '-')                   AS "tpa",
                i.claim_no                                  AS "claim_no",
                COALESCE(b.bill_number, '-')                AS "bill_no",
                b.bill_date                                 AS "bill_date",
                b.bill_amount / 100.0                       AS "bill_amount",
                COALESCE(i.preauth_approved_limit, 0) / 100.0 AS "sanctioned",
                COALESCE(chq.received, 0) / 100.0           AS "received",
                (b.bill_amount - b.discount_total
                 - COALESCE(chq.received, 0)) / 100.0       AS "outstanding",
                COALESCE(i.insurance_current_status, 'LEGACY') AS "stage",
                (?::DATE - b.bill_date)                     AS "age_days"
            FROM insurances i
            JOIN bills b ON b.id = i.bill_id
            LEFT JOIN LATERAL (
                SELECT SUM(r.amount) AS received
                FROM insurance_cheque_receipts r
                WHERE r.insurance_id = i.id AND r.status = 1
            ) chq ON TRUE
        """ + PATIENT_JOIN + """
            WHERE b.bill_date <= ?::DATE
              AND b.status = 1
              AND (b.bill_amount - b.discount_total - COALESCE(chq.received, 0)) > 0
            """);
        List<Object> args = new ArrayList<>(List.of(asOnDate, asOnDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ORDER BY b.bill_date ASC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 10. AGEING ANALYSIS
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getAgeingAnalysis(String asOnDate, String payer) {
        StringBuilder sql = new StringBuilder("""
            SELECT * FROM (
              SELECT
        """ + PATIENT_COLS + """
                  i.insurer_name                            AS "insurer",
                  COALESCE(i.tpa_name, '-')                 AS "tpa",
                  i.claim_no                                AS "claim_no",
                  COALESCE(b.bill_number, '-')              AS "bill_no",
                  b.bill_date                               AS "bill_date",
                  i.dispatch_date                           AS "dispatched_on",
                  (b.bill_amount - b.discount_total
                   - COALESCE(chq.received, 0)) / 100.0     AS "outstanding",
                  (?::DATE - b.bill_date)   AS "age_days",
                  CASE
                    WHEN (?::DATE - b.bill_date) < 31  THEN 'Less than 31 days'
                    WHEN (?::DATE - b.bill_date) <= 60 THEN '31 to 60 days'
                    WHEN (?::DATE - b.bill_date) <= 90 THEN '61 to 90 days'
                    WHEN (?::DATE - b.bill_date) <= 120 THEN '91 to 120 days'
                    WHEN (?::DATE - b.bill_date) <= 150 THEN '121 to 150 days'
                    ELSE 'More than 150 days'
                  END                                       AS "ageing_bracket"
              FROM insurances i
              JOIN bills b ON b.id = i.bill_id
              LEFT JOIN LATERAL (
                  SELECT SUM(r.amount) AS received
                  FROM insurance_cheque_receipts r
                  WHERE r.insurance_id = i.id AND r.status = 1
              ) chq ON TRUE
        """ + PATIENT_JOIN + """
              WHERE b.bill_date <= ?::DATE
                AND b.status = 1
                AND (b.bill_amount - b.discount_total - COALESCE(chq.received, 0)) > 0
            """);
        List<Object> args = new ArrayList<>(List.of(
            asOnDate, asOnDate, asOnDate, asOnDate, asOnDate, asOnDate, asOnDate));

        if (isFiltered(payer)) {
            sql.append(" AND i.insurer_name = ? ");
            args.add(payer);
        }
        sql.append(scope.predicate("i"));
        args.addAll(scope.args());
        sql.append(" ) aged ORDER BY aged.\"age_days\" DESC ");

        return ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    /**
     * Maps the six bracket ids exposed by {@code GET /insurance/getAgeingCriteria}
     * onto the labels this query produces. Returns null for "all", so no filter
     * is applied.
     */
    private String ageingBracketLabel(String bracket) {
        if (!isFiltered(bracket)) return null;
        return switch (bracket.trim()) {
            case "1" -> "Less than 31 days";
            case "2" -> "31 to 60 days";
            case "3" -> "61 to 90 days";
            case "4" -> "91 to 120 days";
            case "5" -> "121 to 150 days";
            case "6" -> "More than 150 days";
            // Already a label rather than an id — the frontend sends one or the
            // other depending on which control the user came from.
            default  -> bracket;
        };
    }

    /** "ALL", empty and null all mean "do not filter", matching the other report modules. */
    private boolean isFiltered(String value) {
        return value != null && !value.isBlank() && !"ALL".equalsIgnoreCase(value.trim());
    }
}
