package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RevenueReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getConsultantRevenueReport(String fromDate, String toDate) {
        String sql = """
            SELECT
                b.bill_date,
                c.first_name || ' ' || c.last_name         AS consultant_name,
                d.name                                      AS department,
                COUNT(DISTINCT b.id)                        AS bill_count,
                ROUND(SUM(b.bill_amount) / 100.0, 2)                 AS gross_revenue,
                ROUND(SUM(b.discount_total) / 100.0, 2)              AS discount,
                ROUND(SUM(b.bill_amount - b.discount_total) / 100.0, 2) AS net_revenue,
                ROUND(SUM(b.payment_total) / 100.0, 2)               AS collected
            FROM bills b
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
            GROUP BY b.bill_date, c.id, c.first_name, c.last_name, d.name
            ORDER BY b.bill_date, net_revenue DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getDepartmentRevenueReport(String fromDate, String toDate) {
        String sql = """
            SELECT
                sc.name                                     AS service_category,
                COUNT(DISTINCT b.id)                        AS bill_count,
                ROUND(SUM(cli.amount) / 100.0, 2)                    AS gross_revenue,
                ROUND(SUM(cli.amount * (b.discount_total::float / NULLIF(b.bill_amount,0))) / 100.0, 2) AS apportioned_discount,
                COUNT(cli.id)                               AS line_items
            FROM charge_line_items cli
            JOIN bills b ON cli.bill_id = b.id
            JOIN service_catalog_items sci ON cli.service_catalog_item_id = sci.id
            JOIN service_categories sc ON sci.category_id = sc.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
              AND (cli.line_status IS NULL OR cli.line_status != 1)
            GROUP BY sc.id, sc.name
            ORDER BY gross_revenue DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getRoomRevenueReport(String fromDate, String toDate, java.util.UUID bedTypeId) {
        String sql = """
            SELECT
                bed.name                                    AS bed_no,
                b.bill_number                               AS bill_no,
                sn_pat.value                                AS patient_id,
                pat.first_name || ' ' || pat.last_name      AS patient_name,
                c.first_name || ' ' || c.last_name          AS consultant_name,
                ce.started_at                               AS admission_date,
                ROUND(b.bill_amount / 100.0, 2)             AS bill_amount,
                ROUND(b.payment_total / 100.0, 2)           AS paid_amount,
                ROUND((b.bill_amount - b.discount_total) / 100.0, 2) AS net_amount
            FROM bills b
            JOIN clinical_encounters ce ON b.encounter_id = ce.id
            JOIN beds bed ON ce.last_bed_id = bed.id
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
              AND (?::UUID IS NULL OR bed.room_category_id = ?::UUID)
            ORDER BY bed.name ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, bedTypeId, bedTypeId);
    }

    public List<Map<String, Object>> getConsultantRevenueOPIP(String fromDate, String toDate) {
        String sql = """
            SELECT
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''), 'Unknown') AS consultant_name,
                ROUND(SUM(CASE WHEN b.encounter_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 2) AS op_bills,
                ROUND(SUM(CASE WHEN b.encounter_type = 1 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 2) AS ip_bills,
                ROUND(SUM(b.bill_amount - b.discount_total) / 100.0, 2) AS total
            FROM bills b
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
            GROUP BY c.id, c.first_name, c.last_name, c.qualification
            ORDER BY total DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getDepartmentRevenueOPIP(String fromDate, String toDate) {
        String sql = """
            SELECT
                sc.name AS department,
                ROUND(SUM(CASE WHEN b.encounter_type = 0 THEN cli.amount ELSE 0 END) / 100.0, 2) AS op_bills,
                ROUND(SUM(CASE WHEN b.encounter_type = 1 THEN cli.amount ELSE 0 END) / 100.0, 2) AS ip_bills,
                ROUND(SUM(cli.amount) / 100.0, 2) AS total
            FROM charge_line_items cli
            JOIN bills b ON cli.bill_id = b.id
            JOIN service_catalog_items sci ON cli.service_catalog_item_id = sci.id
            JOIN service_categories sc ON sci.category_id = sc.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
              AND (cli.line_status IS NULL OR cli.line_status != 1)
            GROUP BY sc.id, sc.name
            ORDER BY total DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getBillsRaisedDaywise(String fromDate, String toDate) {
        String sql = """
            SELECT
                b.bill_number                               AS bill_number,
                b.bill_date                                 AS bill_date,
                sn_pat.value                                AS patient_number,
                COALESCE(pat.salutation || ' ', '') || pat.first_name || ' ' || pat.last_name AS patient_name,
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS consultant,
                ROUND(b.bill_amount / 100.0, 2)                      AS bill_amount,
                ROUND(b.discount_total / 100.0, 2)                   AS discount,
                ROUND((b.bill_amount - b.discount_total) / 100.0, 2) AS net_amount,
                COALESCE(u.username, '')                   AS raised_by,
                b.encounter_type,
                b.bill_type
            FROM bills b
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON b.created_by = u.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
            ORDER BY b.encounter_type ASC, b.bill_type ASC, b.bill_date ASC, b.created_at ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getBillCancelledSummary(String fromDate, String toDate) {
        String sql = """
            SELECT 
                ROUND(COALESCE(SUM(CASE WHEN b.encounter_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0), 2) AS "OP_CAN_AMOUNT",
                ROUND(COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0), 2) AS "IP_CASH_CAN_AMOUNT",
                ROUND(COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type IN (1, 2) THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0), 2) AS "IP_CREDIT_CAN_AMOUNT"
            FROM bills b
            WHERE b.bill_status = 4
              AND b.cancelled_at::DATE BETWEEN ?::DATE AND ?::DATE
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }
}
