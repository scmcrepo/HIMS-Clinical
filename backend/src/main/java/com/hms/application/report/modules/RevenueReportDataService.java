package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                COALESCE(d.name, 'No Department')           AS department,
                COUNT(DISTINCT b.id)                        AS bill_count,
                ROUND(SUM(cli.amount) / 100.0, 2)                    AS gross_revenue,
                ROUND(SUM(cli.amount * (b.discount_total::float / NULLIF(b.bill_amount,0))) / 100.0, 2) AS apportioned_discount,
                COUNT(cli.id)                               AS line_items
            FROM charge_line_items cli
            JOIN bills b ON cli.bill_id = b.id
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
              AND (cli.line_status IS NULL OR cli.line_status != 1)
            GROUP BY d.id, d.name
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
                pat.first_name || ' ' || pat.last_name     AS patient_name,
                (CASE
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, pat.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, pat.estimated_date_of_birth))::text || 'd'
                END || '/' || CASE pat.gender WHEN 0 THEN 'M' WHEN 1 THEN 'F' ELSE 'O' END) AS age_sex,
                COALESCE(con.first_name || ' ' || con.last_name, '') AS consultant_name,
                TO_CHAR(ce.started_at, 'DD/MM/YYYY')       AS admission_date,
                rc.name                                     AS room_category,
                ROUND(SUM(cli.amount) / 100.0, 2)          AS bill_amount,
                ROUND(SUM(cli.amount - cli.discount_amount) / 100.0, 2) AS paid_amount
            FROM charge_line_items cli
            JOIN bills b ON cli.bill_id = b.id
            JOIN patients pat ON b.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants con ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = con.id
            JOIN bed_occupancies bo ON b.encounter_id = bo.encounter_id
            JOIN beds bed ON bo.bed_id = bed.id
            JOIN room_categories rc ON bed.room_category_id = rc.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
              AND (cli.line_status IS NULL OR cli.line_status != 1)
              AND cli.bed_charge_from IS NOT NULL
              AND (?::UUID IS NULL OR rc.id = ?::UUID)
            GROUP BY bed.id, bed.name, b.bill_number,
                     sn_pat.value, pat.first_name, pat.last_name, pat.estimated_date_of_birth, pat.gender,
                     con.first_name, con.last_name, ce.started_at, rc.name
            ORDER BY bill_amount DESC
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
                COALESCE(d.name, 'No Department') AS department,
                ROUND(SUM(CASE WHEN b.encounter_type = 0 THEN cli.amount ELSE 0 END) / 100.0, 2) AS op_bills,
                ROUND(SUM(CASE WHEN b.encounter_type = 1 THEN cli.amount ELSE 0 END) / 100.0, 2) AS ip_bills,
                ROUND(SUM(cli.amount) / 100.0, 2) AS total
            FROM charge_line_items cli
            JOIN bills b ON cli.bill_id = b.id
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE
              AND b.bill_status != 4
              AND (cli.line_status IS NULL OR cli.line_status != 1)
            GROUP BY d.id, d.name
            ORDER BY total DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getBillsRaisedDaywise(String fromDate, String toDate, String departmentId, String consultantId) {
        String sql = """
            SELECT
                b.bill_date                                 AS bill_date,
                b.bill_number                               AS bill_number,
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
            """;

        List<Object> args = new ArrayList<>();
        args.add(fromDate);
        args.add(toDate);

        if (consultantId != null && !"ALL".equalsIgnoreCase(consultantId) && !consultantId.trim().isEmpty()) {
            sql += " AND c.id = ?::UUID ";
            args.add(UUID.fromString(consultantId.trim()));
        }
        if (departmentId != null && !"ALL".equalsIgnoreCase(departmentId) && !departmentId.trim().isEmpty()) {
            sql += " AND c.department_id = ?::UUID ";
            args.add(UUID.fromString(departmentId.trim()));
        }

        sql += " ORDER BY b.encounter_type ASC, b.bill_type ASC, b.bill_date ASC, b.created_at ASC ";
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, args.toArray());
    }

    public List<Map<String, Object>> getBillCancelledSummary(String fromDate, String toDate, String departmentId, String consultantId) {
        String sql = """
            SELECT 
                ROUND(COALESCE(SUM(CASE WHEN b.encounter_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0), 2) AS "OP_CAN_AMOUNT",
                ROUND(COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type = 0 THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0), 2) AS "IP_CASH_CAN_AMOUNT",
                ROUND(COALESCE(SUM(CASE WHEN b.encounter_type = 1 AND b.bill_type IN (1, 2) THEN b.bill_amount - b.discount_total ELSE 0 END) / 100.0, 0.0), 2) AS "IP_CREDIT_CAN_AMOUNT"
            FROM bills b
            LEFT JOIN clinical_encounters ce ON b.encounter_id = ce.id
            LEFT JOIN consultants c ON COALESCE(b.primary_provider_id, ce.primary_provider_id) = c.id
            WHERE b.bill_status = 4
              AND b.cancelled_at::DATE BETWEEN ?::DATE AND ?::DATE
            """;

        List<Object> args = new ArrayList<>();
        args.add(fromDate);
        args.add(toDate);

        if (consultantId != null && !"ALL".equalsIgnoreCase(consultantId) && !consultantId.trim().isEmpty()) {
            sql += " AND c.id = ?::UUID ";
            args.add(UUID.fromString(consultantId.trim()));
        }
        if (departmentId != null && !"ALL".equalsIgnoreCase(departmentId) && !departmentId.trim().isEmpty()) {
            sql += " AND c.department_id = ?::UUID ";
            args.add(UUID.fromString(departmentId.trim()));
        }

        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, args.toArray());
    }
}
