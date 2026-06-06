package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CollectionReportDataService {

    private final JdbcTemplate jdbcTemplate;

    // ────────────────────────────────────────────────────────────────────────
    // 1. NET COLLECTION SUMMARY  (user-wise, matching legacy JRXML layout)
    //    Columns: User | Cash (Collection) | Petty Cash | Cash In Hand
    //           | Cheque | Card | Fund Transfer | Net
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getNetCollectionSummary(String fromDate, String toDate) {
        String sql = """
            SELECT
                u.username                                                        AS user,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CASH'
                    AND p.payment_type IN ('PAYMENT','DEPOSIT')), 0) / 100.0      AS collection_cash,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CASH'
                    AND p.payment_type IN ('PAYMENT','DEPOSIT')), 0) / 100.0      AS cash_in_hand,

                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CARD'
                    AND p.payment_type IN ('PAYMENT','DEPOSIT')), 0) / 100.0      AS card,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'UPI'
                    AND p.payment_type IN ('PAYMENT','DEPOSIT')), 0) / 100.0      AS upi,
                (
                  COALESCE(SUM(p.amount) FILTER (WHERE p.payment_type IN ('PAYMENT','DEPOSIT')), 0)
                  - COALESCE(SUM(p.amount) FILTER (WHERE p.payment_type IN ('REFUND','ADVANCE_REFUND')), 0)
                ) / 100.0                                                         AS net
            FROM payments p
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.payment_date BETWEEN ?::DATE AND ?::DATE
              AND p.status = 'Active'
            GROUP BY u.id, u.username
            ORDER BY u.username
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 2. RECEIPTS SUMMARY  (mode-wise totals for dashboard card)
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getReceiptsSummary(String fromDate, String toDate) {
        String sql = """
            SELECT
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CASH'), 0) / 100.0      AS cash,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CARD'), 0) / 100.0      AS card,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'UPI'), 0) / 100.0       AS upi,
                COALESCE(SUM(p.amount), 0) / 100.0                                             AS net_amount
            FROM payments p
            WHERE p.payment_date BETWEEN ?::DATE AND ?::DATE
              AND p.payment_type IN ('PAYMENT', 'DEPOSIT')
              AND p.status = 'Active'
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 3. RECEIPTS DETAIL  (individual rows matching legacy Receipt Detail)
    //    Columns: Receipt No | Rcpt Date | Bill No | Bill Date | Patient No
    //           | Patient | Mode | Payment Details | Amount (Rs) | User
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getReceiptsDetail(String fromDate, String toDate, String user) {
        String sql = """
            SELECT
                p.sequence_number                           AS receipt_no,
                p.payment_date                              AS rcpt_date,
                b.bill_number                                         AS bill_no,
                b.bill_date                                 AS bill_date,
                sn_pat.value                                AS patient_no,
                pat.first_name || ' ' || pat.last_name      AS patient,
                p.payment_mode                              AS mode,
                ''                                          AS payment_details,
                ROUND(p.amount / 100.0, 2)                  AS amount,
                u.username                                  AS "user"
            FROM payments p
            JOIN bills b ON p.bill_id = b.id
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.payment_date BETWEEN ?::DATE AND ?::DATE
              AND p.payment_type IN ('PAYMENT', 'DEPOSIT')
              AND p.status = 'Active'
            """;
        if (user != null && !"ALL".equalsIgnoreCase(user) && !user.trim().isEmpty()) {
            sql += " AND u.username = ? ";
            sql += " ORDER BY p.payment_date, p.created_at ";
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, user);
        }
        sql += " ORDER BY p.payment_date, p.created_at ";
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 4. DEPOSITS SUMMARY  (mode-wise totals for dashboard card)
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getDepositsSummary(String fromDate, String toDate) {
        String sql = """
            SELECT
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CASH'), 0) / 100.0      AS cash,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CARD'), 0) / 100.0      AS card,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'UPI'), 0) / 100.0       AS upi,
                COALESCE(SUM(p.amount), 0) / 100.0                                             AS net_amount
            FROM payments p
            WHERE p.payment_date BETWEEN ?::DATE AND ?::DATE
              AND p.payment_type = 'DEPOSIT'
              AND p.status = 'Active'
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 5. DEPOSITS DETAIL  (individual rows matching legacy Deposit Detail)
    //    Columns: Deposit No | Dpst Date | Patient No | Patient | Deposit
    //           | Adj against Bill | Bill Date | Adj Amnt | Balance
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getDepositsDetail(String fromDate, String toDate, String user) {
        String sql = """
            SELECT
                p.sequence_number                           AS deposit_no,
                p.payment_date                              AS dpst_date,
                sn_pat.value                                AS patient_no,
                pat.first_name || ' ' || pat.last_name      AS patient,
                ROUND(p.amount / 100.0, 2)                  AS deposit,
                sn_b.value                                  AS adj_against_bill,
                b.bill_date                                 AS bill_date,
                ROUND(p.amount / 100.0, 2)                  AS adj_amnt,
                0                                           AS balance,
                u.username                                  AS "user"
            FROM payments p
            JOIN bills b ON p.bill_id = b.id
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN number_sequences sn_b ON b.id = sn_b.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.payment_date BETWEEN ?::DATE AND ?::DATE
              AND p.payment_type = 'DEPOSIT'
              AND p.status = 'Active'
            """;
        if (user != null && !"ALL".equalsIgnoreCase(user) && !user.trim().isEmpty()) {
            sql += " AND u.username = ? ";
            sql += " ORDER BY p.payment_date, p.created_at ";
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, user);
        }
        sql += " ORDER BY p.payment_date, p.created_at ";
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getDiscountsDetail(String fromDate, String toDate) {
        String sql = """
            SELECT
                COALESCE(da.created_at::DATE, b.bill_date) AS discount_date,
                b.bill_number                     AS bill_no,
                sn_pat.value                      AS patient_no,
                pat.first_name || ' ' || pat.last_name     AS patient,
                COALESCE(da.reason, '')           AS reason,
                ROUND(b.bill_amount / 100.0, 2)             AS bill_amount,
                ROUND(b.discount_total / 100.0, 2)          AS discount,
                ROUND((b.bill_amount - b.discount_total) / 100.0, 2) AS net_amount,
                u.username                        AS "user"
            FROM bills b
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN discount_adjustments da ON da.bill_id = b.id AND da.is_active = TRUE
            LEFT JOIN users u ON COALESCE(da.created_by, b.modified_by, b.created_by) = u.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.discount_total > 0
              AND b.bill_status != 4
            ORDER BY discount_date, b.created_at
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 6. REFUNDS SUMMARY  (mode-wise totals for dashboard card)
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getRefundsSummary(String fromDate, String toDate) {
        String sql = """
            SELECT
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CASH'), 0) / 100.0      AS cash,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'CARD'), 0) / 100.0      AS card,
                COALESCE(SUM(p.amount) FILTER (WHERE p.payment_mode = 'UPI'), 0) / 100.0       AS upi,
                COALESCE(SUM(p.amount), 0) / 100.0                                             AS net_amount
            FROM payments p
            WHERE p.payment_date BETWEEN ?::DATE AND ?::DATE
              AND p.payment_type IN ('REFUND','ADVANCE_REFUND')
              AND p.status = 'Active'
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 7. REFUNDS DETAIL  (individual rows for detail view)
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getRefundsDetail(String fromDate, String toDate, String visit, String user) {
        Integer encounterType = null;
        if ("OP".equalsIgnoreCase(visit)) encounterType = 0;
        else if ("IP".equalsIgnoreCase(visit)) encounterType = 1;
        
        String sql = """
            SELECT
                p.sequence_number                           AS refund_no,
                p.payment_date                              AS refund_date,
                b.bill_number                                         AS bill_no,
                b.bill_date                                 AS bill_date,
                sn_pat.value                                AS patient_no,
                pat.first_name || ' ' || pat.last_name      AS patient_name,
                p.payment_mode                              AS mode,
                ROUND(p.amount / 100.0, 2)                  AS amount,
                u.username                                  AS "user",
                p.notes                                     AS refund_reason
            FROM payments p
            JOIN bills b ON p.bill_id = b.id
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.payment_date BETWEEN ?::DATE AND ?::DATE
              AND p.payment_type IN ('REFUND','ADVANCE_REFUND')
              AND p.status = 'Active'
            """;
        
        List<Object> args = new ArrayList<>();
        args.add(fromDate);
        args.add(toDate);
        
        if (encounterType != null) {
            sql += "  AND b.encounter_type = ? ";
            args.add(encounterType);
        }
        if (user != null && !"ALL".equalsIgnoreCase(user) && !user.trim().isEmpty()) {
            sql += "  AND u.username = ? ";
            args.add(user);
        }
        
        sql += " ORDER BY p.payment_date, p.created_at ";
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, args.toArray());
    }

    // ────────────────────────────────────────────────────────────────────────
    // 8. PETTY CASH SUMMARY  (placeholder — returns zeroes)
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getPettyCashSummary(String fromDate, String toDate) {
        String sql = """
            SELECT
                0 AS cash,
                0 AS net_amount
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 9. PETTY CASH DETAIL  (placeholder — returns empty list)
    // ────────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getPettyCashDetail(String fromDate, String toDate) {
        return List.of();
    }
}
