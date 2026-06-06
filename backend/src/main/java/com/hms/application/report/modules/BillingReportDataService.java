package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getBillsRaisedDaywise(String fromDate, String toDate, String visit) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                b.bill_date                                 AS bill_date,
                b.bill_number                               AS bill_number,
                sn_pat.value                                AS patient_number,
                COALESCE(pat.salutation || ' ', '') || pat.first_name || ' ' || pat.last_name AS patient_name,
                CASE
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                CASE pat.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS consultant,
                CASE b.encounter_type WHEN 0 THEN 'OP' WHEN 1 THEN 'IP' ELSE b.encounter_type::text END AS encounter_type,
                CASE b.bill_type WHEN 0 THEN 'Cash' WHEN 1 THEN 'Credit' WHEN 2 THEN 'Credit' ELSE b.bill_type::text END AS bill_type,
                b.bill_amount / 100.0                      AS bill_amount,
                b.discount_total / 100.0                   AS discount,
                (b.bill_amount - b.discount_total) / 100.0 AS net_amount,
                COALESCE(u.username, '')                   AS raised_by
            FROM bills b
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON b.created_by = u.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
            """);

        List<Object> args = new java.util.ArrayList<>();
        args.add(fromDate);
        args.add(toDate);

        if ("OP".equalsIgnoreCase(visit)) {
            sql.append(" AND b.encounter_type = 0");
        } else if ("IP".equalsIgnoreCase(visit)) {
            sql.append(" AND b.encounter_type = 1");
        }

        sql.append(" ORDER BY b.encounter_type ASC, b.bill_type ASC, b.bill_date ASC, b.created_at ASC");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getBillsCancelledDaywise(String fromDate, String toDate, String visit) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                b.cancelled_at::DATE                        AS cancelled_date,
                b.bill_number                               AS cancelled_bill_no,
                sn_pat.value                                AS patient_number,
                COALESCE(pat.salutation || ' ', '') || pat.first_name || ' ' || pat.last_name AS patient_name,
                CASE
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                CASE pat.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS consultant,
                CASE b.encounter_type WHEN 0 THEN 'OP' WHEN 1 THEN 'IP' ELSE b.encounter_type::text END AS encounter_type,
                CASE b.bill_type WHEN 0 THEN 'Cash' WHEN 1 THEN 'Credit' WHEN 2 THEN 'Credit' ELSE b.bill_type::text END AS bill_type,
                b.bill_amount / 100.0                      AS bill_amount,
                b.discount_total / 100.0                   AS discount,
                (b.bill_amount - b.discount_total) / 100.0 AS net_amount,
                COALESCE(u.username, '')                   AS cancelled_by
            FROM bills b
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON b.modified_by = u.id
            WHERE b.bill_status = 4
              AND b.cancelled_at::DATE BETWEEN ?::DATE AND ?::DATE
            """);

        List<Object> args = new java.util.ArrayList<>();
        args.add(fromDate);
        args.add(toDate);

        if ("OP".equalsIgnoreCase(visit)) {
            sql.append(" AND b.encounter_type = 0");
        } else if ("IP".equalsIgnoreCase(visit)) {
            sql.append(" AND b.encounter_type = 1");
        }

        sql.append(" ORDER BY b.cancelled_at ASC, b.created_at ASC");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getDiscountReport(String fromDate, String toDate, String visit) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                COALESCE(da.created_at::DATE, b.bill_date) AS discount_date,
                b.bill_number                     AS bill_number,
                sn_pat.value                      AS patient_number,
                pat.first_name || ' ' || pat.last_name     AS patient_name,
                (CASE
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'd'
                END || '/' || CASE pat.gender WHEN 0 THEN 'M' WHEN 1 THEN 'F' ELSE 'O' END) AS age_sex,
                COALESCE(da.reason, '')           AS reason,
                b.bill_amount / 100.0             AS bill_amount,
                b.discount_total / 100.0          AS discount_amount,
                u.username                        AS given_by
            FROM bills b
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN discount_adjustments da ON da.bill_id = b.id AND da.is_active = TRUE
            LEFT JOIN users u ON COALESCE(da.created_by, b.modified_by, b.created_by) = u.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.discount_total > 0
              AND b.bill_status != 4
            """);

        List<Object> args = new java.util.ArrayList<>();
        args.add(fromDate);
        args.add(toDate);

        if ("OP".equalsIgnoreCase(visit)) {
            sql.append(" AND b.encounter_type = 0");
        } else if ("IP".equalsIgnoreCase(visit)) {
            sql.append(" AND b.encounter_type = 1");
        }

        sql.append(" ORDER BY COALESCE(da.created_at::DATE, b.bill_date) ASC, b.created_at ASC");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getBillsOverdue() {
        String sql = """
            SELECT
                b.bill_date                       AS bill_date,
                ce.started_at                     AS admission_date,
                COALESCE(bed.name, '-')           AS bed_no,
                sn_pat.value                      AS patient_no,
                pat.first_name || ' ' || pat.last_name     AS patient,
                CASE
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                CASE pat.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                b.bill_amount / 100.0             AS bill_amount,
                (b.bill_amount - b.discount_total) / 100.0 AS net_amount,
                b.payment_total / 100.0           AS paid,
                (b.bill_amount - b.discount_total - b.payment_total) / 100.0 AS due_amount
            FROM bills b
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN beds bed ON ce.last_bed_id = bed.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            WHERE b.encounter_type = 1
              AND b.bill_status NOT IN (1, 4)
              AND (b.bill_amount - b.discount_total - b.payment_total) > COALESCE((
                  SELECT SUM(p.amount) FROM payments p WHERE p.bill_id = b.id AND p.payment_type = 'DEPOSIT' AND p.status = 'Active'
              ), 0)
            ORDER BY ce.started_at DESC, (b.bill_amount - b.discount_total - b.payment_total) DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql);
    }

    public List<Map<String, Object>> getUnsettledBills(String fromDate, String toDate, String visit) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                b.bill_date                       AS bill_date,
                b.bill_number                     AS bill_number,
                sn_pat.value                      AS patient_number,
                pat.first_name || ' ' || pat.last_name     AS patient_name,
                CASE
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                CASE pat.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                CASE b.encounter_type WHEN 0 THEN 'OP' WHEN 1 THEN 'IP' ELSE b.encounter_type::text END AS encounter_type,
                pat.contact_number,
                ROUND(b.bill_amount / 100.0, 2)                      AS gross_amount,
                ROUND(b.discount_total / 100.0, 2)                   AS discount,
                ROUND(b.payment_total / 100.0, 2)                    AS paid_amount,
                ROUND((b.bill_amount - b.discount_total - b.payment_total) / 100.0, 2) AS balance_due,
                CASE b.bill_status WHEN 0 THEN 'Draft' WHEN 1 THEN 'Active' WHEN 2 THEN 'Settled' WHEN 3 THEN 'Overdue' WHEN 4 THEN 'Cancelled' ELSE b.bill_status::text END AS status
            FROM bills b
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            WHERE b.bill_status NOT IN (2, 4)
              AND b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND (b.bill_amount - b.discount_total - b.payment_total) > 0
        """);

        List<Object> args = new java.util.ArrayList<>(List.of(fromDate, toDate));
        if ("OP".equalsIgnoreCase(visit)) {
            sql.append(" AND b.encounter_type = 0");
        } else if ("IP".equalsIgnoreCase(visit)) {
            sql.append(" AND b.encounter_type = 1");
        }

        sql.append(" ORDER BY b.bill_date, balance_due DESC");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getBillRaisedSummary(String fromDate, String toDate) {
        String sql = """
            SELECT 
                COALESCE(SUM(CASE WHEN b.encounter_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0) AS "OP_Bill_AMOUNT",
                COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0) AS "IP_CASH_Bill_AMOUNT",
                COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type IN (1, 2) THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0) AS "IP_CREDIT_Bill_AMOUNT"
            FROM bills b
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getBillCancelledSummary(String fromDate, String toDate) {
        String sql = """
            SELECT 
                COALESCE(SUM(CASE WHEN b.encounter_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0) AS "OP_CAN_AMOUNT",
                COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0) AS "IP_CASH_CAN_AMOUNT",
                COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type IN (1, 2) THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0) AS "IP_CREDIT_CAN_AMOUNT"
            FROM bills b
            WHERE b.bill_status = 4
              AND b.cancelled_at::DATE BETWEEN ?::DATE AND ?::DATE
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getDiscountSummary(String fromDate, String toDate) {
        String sql = """
            SELECT 
                COALESCE(SUM(CASE WHEN b.encounter_type = 0 THEN b.discount_total ELSE 0 END) / 100.0, 0.0) AS "OP_DIS_AMOUNT",
                COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type = 0 THEN b.discount_total ELSE 0 END) / 100.0, 0.0) AS "IP_CASH_DIS_AMOUNT",
                COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type IN (1, 2) THEN b.discount_total ELSE 0 END) / 100.0, 0.0) AS "IP_CREDIT_DIS_AMOUNT"
            FROM bills b
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
              AND b.discount_total > 0
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getOutstandingBillsSummary(String fromDate, String toDate) {
        String sql = """
            SELECT 
                COALESCE(SUM(CASE WHEN b.encounter_type = 0 THEN (b.bill_amount - b.discount_total - b.payment_total - b.service_refund_total + b.refund_total) ELSE 0 END) / 100.0, 0.0) AS "OP_OUT_AMOUNT",
                COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type = 0 THEN (b.bill_amount - b.discount_total - b.payment_total - b.service_refund_total + b.refund_total) ELSE 0 END) / 100.0, 0.0) AS "IP_CASH_OUT_AMOUNT",
                COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type IN (1, 2) THEN (b.bill_amount - b.discount_total - b.payment_total - b.service_refund_total + b.refund_total) ELSE 0 END) / 100.0, 0.0) AS "IP_CREDIT_OUT_AMOUNT"
            FROM bills b
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getIpOutstandingBillsSummary(String fromDate, String toDate) {
        String sql = """
            SELECT 
                p.name AS "payor", 
                COALESCE(SUM(b.payment_total) / 100.0, 0.0) AS "paid_amount",
                COALESCE(SUM(b.bill_amount - b.service_refund_total - b.discount_total) / 100.0, 0.0) AS "net_amount",
                COALESCE(SUM(b.bill_amount - b.discount_total - b.payment_total - b.service_refund_total + b.refund_total) / 100.0, 0.0) AS "balanced_amount"
            FROM bills b
            JOIN payors p ON p.id = b.payor_id
            WHERE b.encounter_type = 1
              AND b.bill_status != 4
              AND b.bill_date BETWEEN ?::DATE AND ?::DATE
            GROUP BY p.id, p.name
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getOverDueBillRaisedSummary() {
        String sql = """
            SELECT 
                COALESCE(SUM(b.bill_amount - b.discount_total) / 100.0, 0.0) AS "bill_amount",
                COALESCE(SUM(b.payment_total) / 100.0, 0.0) AS "paid_amount",
                COALESCE(SUM(b.bill_amount - b.discount_total - b.payment_total) / 100.0, 0.0) AS "due_amount"
            FROM bills b
            WHERE b.encounter_type = 1
              AND b.bill_status NOT IN (1, 4)
              AND (b.bill_amount - b.discount_total - b.payment_total) > COALESCE((
                  SELECT SUM(p.amount) FROM payments p WHERE p.bill_id = b.id AND p.payment_type = 'DEPOSIT' AND p.status = 'Active'
              ), 0)
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql);
    }
}
