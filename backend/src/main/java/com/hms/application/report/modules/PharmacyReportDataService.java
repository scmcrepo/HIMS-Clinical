package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PharmacyReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getPharmacySalesBillsReport(String fromDate, String toDate) {
        String sql = """
            SELECT
                ps.sale_date,
                ps.sequence_number                          AS sale_number,
                COALESCE(pat.first_name || ' ' || pat.last_name, ps.customer_name) AS customer_name,
                sn_pat.value                      AS patient_number,
                ps.consultant_name                         AS prescribed_by,
                COUNT(psl.id)                               AS items_count,
                SUM(psl.quantity)                           AS total_qty,
                ROUND(SUM(psl.amount) / 100.0, 2)                    AS gross_amount,
                ROUND(ps.discount_amount / 100.0, 2)                 AS discount,
                ROUND((SUM(psl.amount) - ps.discount_amount) / 100.0, 2) AS net_amount,
                CASE ps.sale_status 
                    WHEN 0 THEN 'Draft' 
                    WHEN 1 THEN 'Billed' 
                    WHEN 2 THEN 'Settled' 
                    WHEN 3 THEN 'With Due' 
                    ELSE ps.sale_status::text 
                END AS bill_status
            FROM pharmacy_sales ps
            LEFT JOIN pharmacy_sale_lines psl ON ps.id = psl.sale_id
            LEFT JOIN patients pat ON ps.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            WHERE ps.sale_date BETWEEN ?::DATE AND ?::DATE
              AND ps.status != 3
            GROUP BY ps.id, ps.sale_date, ps.sequence_number, pat.first_name, pat.last_name,
                     sn_pat.value, ps.customer_name, ps.consultant_name,
                     ps.discount_amount, ps.sale_status
            ORDER BY ps.sale_date DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getStockLedger(UUID deptId) {
        String sql = """
            SELECT ii.name as item_name, d.name as department_name,
                   ib.batch_number, ib.current_quantity, ib.expiry_date, ib.selling_rate
            FROM inventory_batches ib
            JOIN inventory_items ii ON ib.item_id = ii.id
            JOIN departments d ON ib.department_id = d.id
            WHERE ib.department_id = ?
            ORDER BY ii.name
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, deptId);
    }

    public Map<String, Object> getBillDetail(UUID billId) {
        String billSql = """
            SELECT
                b.id,
                sn_b.value as bill_number,
                b.bill_date,
                ROUND(b.bill_amount / 100.0, 2) as total_amount,
                ROUND(b.discount_total / 100.0, 2) as total_discount,
                ROUND(b.payment_total / 100.0, 2) as total_paid,
                ROUND((b.bill_amount - b.discount_total - b.payment_total) / 100.0, 2) as total_due,
                p.first_name || ' ' || p.last_name as patient_name,
                sn_p.value as patient_number,
                p.gender,
                p.estimated_date_of_birth
            FROM bills b
            JOIN patients p ON b.patient_id = p.id
            LEFT JOIN number_sequences sn_b ON b.id = sn_b.id
            LEFT JOIN number_sequences sn_p ON p.id = sn_p.id
            WHERE b.id = ?
            """;
        Map<String, Object> bill = jdbcTemplate.queryForMap(billSql, billId);
        String itemsSql = """
            SELECT
                sci.name as item_name,
                cli.quantity,
                ROUND(cli.unit_rate / 100.0, 2) as unit_rate,
                ROUND(cli.amount / 100.0, 2) as amount,
                sc.name as category
            FROM charge_line_items cli
            JOIN service_catalog_items sci ON cli.service_catalog_item_id = sci.id
            JOIN service_categories sc ON sci.category_id = sc.id
            WHERE cli.bill_id = ? AND (cli.line_status IS NULL OR cli.line_status != 1)
            """;
        bill.put("items", com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, itemsSql, billId));
        return bill;
    }

    public List<Map<String, Object>> getPharmacySalesCollectionSummary(String fromDate, String toDate) {
        String sql = """
            WITH combined AS (
                SELECT 
                    u.username AS username,
                    psp.amount AS amount,
                    psp.payment_mode AS mode,
                    0 AS refund_amount
                FROM pharmacy_sale_payments psp
                JOIN pharmacy_sales ps ON psp.sale_id = ps.id
                LEFT JOIN users u ON psp.created_by = u.id
                WHERE ps.sale_date BETWEEN ?::DATE AND ?::DATE
                
                UNION ALL
                
                SELECT
                    u.username AS username,
                    0 AS amount,
                    NULL AS mode,
                    sr.total_return_amount AS refund_amount
                FROM sales_returns sr
                LEFT JOIN users u ON sr.created_by = u.id
                WHERE sr.return_date BETWEEN ?::DATE AND ?::DATE
            )
            SELECT
                COALESCE(username, 'unknown') AS user_name,
                ROUND(COALESCE(SUM(amount) FILTER (WHERE UPPER(mode) = 'CASH'), 0) / 100.0, 2) AS cash,
                ROUND(COALESCE(SUM(amount) FILTER (WHERE UPPER(mode) IN ('CARD', 'TRANSFER', 'UPI', 'FUND TRANSFER')), 0) / 100.0, 2) AS card,
                ROUND(COALESCE(SUM(amount) FILTER (WHERE UPPER(mode) = 'CHEQUE'), 0) / 100.0, 2) AS cheque,
                ROUND(COALESCE(SUM(amount), 0) / 100.0, 2) AS net,
                ROUND(COALESCE(SUM(refund_amount) * -1, 0) / 100.0, 2) AS refund_cash,
                ROUND((COALESCE(SUM(amount), 0) - COALESCE(SUM(refund_amount), 0)) / 100.0, 2) AS net_amount
            FROM combined
            GROUP BY username
            ORDER BY username
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, fromDate, toDate);
    }

    public List<Map<String, Object>> getPharmacySalesCollectionReceipts(String fromDate, String toDate) {
        String sql = """
            SELECT
                ps.sequence_number AS receipt_no,
                ps.sale_date AS rcpt_date,
                COALESCE(sn_pat.value, '-') AS patient_no,
                COALESCE(pat.first_name || ' ' || pat.last_name, ps.customer_name, 'Walk-in') AS patient,
                ROUND(COALESCE(SUM(psp.amount) FILTER (WHERE UPPER(psp.payment_mode) = 'CASH'), 0) / 100.0, 2) AS cash,
                ROUND(COALESCE(SUM(psp.amount) FILTER (WHERE UPPER(psp.payment_mode) = 'CHEQUE'), 0) / 100.0, 2) AS cheque,
                ROUND(COALESCE(SUM(psp.amount) FILTER (WHERE UPPER(psp.payment_mode) IN ('CARD', 'TRANSFER', 'UPI', 'FUND TRANSFER')), 0) / 100.0, 2) AS card,
                u.username AS user_name
            FROM pharmacy_sale_payments psp
            JOIN pharmacy_sales ps ON psp.sale_id = ps.id
            LEFT JOIN patients pat ON ps.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON psp.created_by = u.id
            WHERE ps.sale_date BETWEEN ?::DATE AND ?::DATE
            GROUP BY ps.id, ps.sequence_number, ps.sale_date, sn_pat.value, pat.first_name, pat.last_name, ps.customer_name, u.username
            ORDER BY ps.sale_date, ps.sequence_number
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getPharmacySalesCollectionRefunds(String fromDate, String toDate) {
        String sql = """
            SELECT
                sr.sequence_number AS receipt_no,
                sr.return_date AS rcpt_date,
                COALESCE(sn_pat.value, '-') AS patient_no,
                COALESCE(pat.first_name || ' ' || pat.last_name, ps.customer_name, 'Walk-in') AS patient,
                ROUND((sr.total_return_amount * -1) / 100.0, 2) AS cash,
                0.00 AS cheque,
                0.00 AS card,
                u.username AS user_name
            FROM sales_returns sr
            JOIN pharmacy_sales ps ON sr.sale_id = ps.id
            LEFT JOIN patients pat ON sr.patient_id = pat.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON sr.created_by = u.id
            WHERE sr.return_date BETWEEN ?::DATE AND ?::DATE
            ORDER BY sr.return_date, sr.sequence_number
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }
}
