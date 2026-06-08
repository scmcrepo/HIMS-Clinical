package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class InventoryReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getCurrentStockReport(UUID itemId) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                ROW_NUMBER() OVER(ORDER BY ii.name, ib.expiry_date) AS "S.No",
                ii.name                                     AS "Product Name",
                ib.batch_number                             AS "Batch No",
                ib.expiry_date                              AS "Expiry Date",
                ib.current_quantity                         AS "Qty",
                ROUND(ib.purchase_rate, 2)                  AS "Purchase Value",
                ROUND(ib.current_quantity * ib.purchase_rate * (1.0 + ii.tax_rate / 100.0), 0) AS "Total Value",
                ROUND(ib.maximum_retail_price * ib.current_quantity, 2) AS "MRP",
                COALESCE(s.name, '-')                       AS "Supplier"
            FROM inventory_batches ib
            JOIN inventory_items ii ON ib.item_id = ii.id
            LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
            LEFT JOIN suppliers s ON pr.supplier_id = s.id
            WHERE ib.current_quantity > 0

            """);
        List<Object> args = new ArrayList<>();
        if (itemId != null) {
            sql.append(" AND ib.item_id = ?");
            args.add(itemId);
        }
        sql.append(" ORDER BY ii.name, ib.expiry_date");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getExpiredItemsReport(String toDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                ROW_NUMBER() OVER(ORDER BY ii.name, ib.expiry_date) AS "S.No",
                ii.name                                     AS "Product Name",
                ib.batch_number                             AS "Batch No",
                ib.expiry_date                              AS "Expiry Date",
                ib.current_quantity                         AS "Qty",
                ROUND(ib.purchase_rate, 2)                  AS "Purchase",
                ROUND(ib.current_quantity * ib.purchase_rate * (1.0 + ii.tax_rate / 100.0), 0) AS "Total Value",
                ROUND(ib.maximum_retail_price * ib.current_quantity, 2) AS "MRP",
                pr.invoice_number                           AS "Invoice No",
                pr.invoice_date                             AS "Invoice Date"
            FROM inventory_batches ib
            JOIN inventory_items ii ON ib.item_id = ii.id
            LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
            WHERE ib.current_quantity > 0
            """);
        List<Object> args = new ArrayList<>();
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append(" AND ib.expiry_date <= ?::DATE");
            args.add(toDate);
        }
        sql.append(" ORDER BY ii.name, ib.expiry_date");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getItemsExpiringWithinMonth(String monthInterval) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                ROW_NUMBER() OVER(ORDER BY ii.name, ib.expiry_date) AS "S.No",
                ii.name                                     AS "Product Name",
                ib.batch_number                             AS "Batch No",
                ib.expiry_date                              AS "Expiry Date",
                ib.current_quantity                         AS "Qty",
                ROUND(ib.purchase_rate, 2)                  AS "Purchase",
                ROUND(ib.current_quantity * ib.purchase_rate * (1.0 + ii.tax_rate / 100.0), 0) AS "Total Value",
                ROUND(ib.maximum_retail_price * ib.current_quantity, 2) AS "MRP",
                pr.invoice_number                           AS "Invoice No",
                pr.invoice_date                             AS "Invoice Date"
            FROM inventory_batches ib
            JOIN inventory_items ii ON ib.item_id = ii.id
            LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
            WHERE ib.current_quantity > 0
            """);
        List<Object> args = new ArrayList<>();
        int months = 1;
        if (monthInterval != null && !monthInterval.trim().isEmpty()) {
            try {
                months = Integer.parseInt(monthInterval.trim());
            } catch (Exception e) {
                // ignore
            }
        }
        sql.append(" AND ib.expiry_date <= CURRENT_DATE + (? || ' month')::INTERVAL");
        args.add(months);

        sql.append(" ORDER BY ii.name, ib.expiry_date");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getSlowMovingItemsReport() {
        StringBuilder sql = new StringBuilder("""
            SELECT
                ROW_NUMBER() OVER(ORDER BY ii.name, ib.expiry_date) AS "S.No",
                ii.name                                     AS "Product Name",
                ib.batch_number                             AS "Batch No",
                ib.expiry_date                              AS "Expiry Date",
                ib.current_quantity                         AS "Qty",
                ROUND(ib.purchase_rate, 2)                  AS "Purchase",
                ROUND(ib.current_quantity * ib.purchase_rate * (1.0 + ii.tax_rate / 100.0), 0) AS "Total Value",
                ROUND(ib.maximum_retail_price * ib.current_quantity, 2) AS "MRP",
                pr.invoice_number                           AS "Invoice No",
                pr.invoice_date                             AS "Invoice Date"
            FROM inventory_batches ib
            JOIN inventory_items ii ON ib.item_id = ii.id
            LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
            LEFT JOIN (
                SELECT ib_sub.item_id
                FROM pharmacy_sale_lines psl
                JOIN inventory_batches ib_sub ON psl.inventory_batch_id = ib_sub.id
                JOIN pharmacy_sales ps ON psl.sale_id = ps.id
                WHERE ps.sale_date >= CURRENT_DATE - INTERVAL '30 days'
                GROUP BY ib_sub.item_id
            ) last_sale ON last_sale.item_id = ii.id
            WHERE ib.current_quantity > 0
              AND last_sale.item_id IS NULL
            """);
        List<Object> args = new ArrayList<>();
        sql.append(" ORDER BY ii.name, ib.expiry_date");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getNilStockReport() {
        StringBuilder sql = new StringBuilder("""
            SELECT
                ROW_NUMBER() OVER(ORDER BY ii.name) AS "S.No",
                ii.name                             AS "Product Name"
            FROM inventory_items ii
            WHERE ii.status = 1
            """);
        sql.append("""
              AND NOT EXISTS (
                  SELECT 1 FROM inventory_batches ib
                  WHERE ib.item_id = ii.id
                    AND ib.current_quantity > 0
              )
            """);
        sql.append(" ORDER BY ii.name");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString());
    }

    public List<Map<String, Object>> getScheduledDrugSalesReport(String fromDate, String toDate, String scheduledDrugType) {
        if (scheduledDrugType == null || scheduledDrugType.trim().isEmpty()) {
            String sql = """
                SELECT
                    ps.sequence_number                          AS bill_no,
                    ps.sale_date                                AS bill_date,
                    sn_pat.value                                AS patient_no,
                    pat.first_name || ' ' || pat.last_name     AS patient_name,
                    ps.consultant_name                          AS consultant,
                    ii.name                                     AS product_name,
                    ii.hsn_code                                 AS product_code,
                    ii.manufacturer                             AS manufacturer,
                    ib.batch_number                             AS batch_number,
                    ib.expiry_date                              AS expiry_date,
                    psl.quantity                                AS quantity,
                    pat.contact_number                          AS contact_no,
                    c.photo_attachment_id                       AS photo_attachment_id
                FROM pharmacy_sales ps
                JOIN pharmacy_sale_lines psl ON ps.id = psl.sale_id
                JOIN inventory_batches ib ON psl.inventory_batch_id = ib.id
                JOIN inventory_items ii ON ib.item_id = ii.id
                LEFT JOIN patients pat ON ps.patient_id = pat.id
                LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
                LEFT JOIN consultants c ON (
                    TRIM(LOWER(ps.consultant_name)) = TRIM(LOWER(c.first_name || ' ' || c.last_name))
                    OR TRIM(LOWER(ps.consultant_name)) = TRIM(LOWER(COALESCE(c.salutation || ' ', '') || c.first_name || ' ' || c.last_name))
                )
                WHERE ps.sale_date BETWEEN ?::DATE AND ?::DATE
                  AND ii.scheduled_drug IS NOT NULL AND ii.scheduled_drug NOT IN ('NON_SCHEDULED', '')
                  AND ps.status != 3
                ORDER BY ps.sale_date DESC, ii.name
                """;
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        } else {
            String sql = """
                SELECT
                    ps.sequence_number                          AS bill_no,
                    ps.sale_date                                AS bill_date,
                    sn_pat.value                                AS patient_no,
                    pat.first_name || ' ' || pat.last_name     AS patient_name,
                    ps.consultant_name                          AS consultant,
                    ii.name                                     AS product_name,
                    ii.hsn_code                                 AS product_code,
                    ii.manufacturer                             AS manufacturer,
                    ib.batch_number                             AS batch_number,
                    ib.expiry_date                              AS expiry_date,
                    psl.quantity                                AS quantity,
                    pat.contact_number                          AS contact_no,
                    c.photo_attachment_id                       AS photo_attachment_id
                FROM pharmacy_sales ps
                JOIN pharmacy_sale_lines psl ON ps.id = psl.sale_id
                JOIN inventory_batches ib ON psl.inventory_batch_id = ib.id
                JOIN inventory_items ii ON ib.item_id = ii.id
                LEFT JOIN patients pat ON ps.patient_id = pat.id
                LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
                LEFT JOIN consultants c ON (
                    TRIM(LOWER(ps.consultant_name)) = TRIM(LOWER(c.first_name || ' ' || c.last_name))
                    OR TRIM(LOWER(ps.consultant_name)) = TRIM(LOWER(COALESCE(c.salutation || ' ', '') || c.first_name || ' ' || c.last_name))
                )
                WHERE ps.sale_date BETWEEN ?::DATE AND ?::DATE
                  AND ii.scheduled_drug = ?
                  AND ps.status != 3
                ORDER BY ps.sale_date DESC, ii.name
                """;
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, scheduledDrugType);
        }
    }

    public List<Map<String, Object>> getItemsBelowReorderLevel() {
        String sql = """
            SELECT
                ii.name                                     AS item_name,
                ii.hsn_code                                 AS item_code,
                c.name                                      AS category,
                d.name                                      AS department,
                COALESCE(SUM(ib.current_quantity), 0)       AS current_stock,
                ii.reorder_level,
                (ii.reorder_level - COALESCE(SUM(ib.current_quantity), 0)) AS shortfall,
                COALESCE(MAX(s.name), '-')                  AS supplier
            FROM inventory_items ii
            LEFT JOIN inventory_batches ib ON ib.item_id = ii.id
            LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
            LEFT JOIN suppliers s ON pr.supplier_id = s.id
            LEFT JOIN departments d ON ib.department_id = d.id
            LEFT JOIN categories c ON ii.category_id = c.id
            WHERE ii.reorder_level IS NOT NULL AND ii.reorder_level > 0
            GROUP BY ii.id, ii.name, ii.hsn_code, c.name, d.name, ii.reorder_level
            HAVING COALESCE(SUM(ib.current_quantity), 0) < ii.reorder_level
            ORDER BY shortfall DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql);
    }

    public List<Map<String, Object>> getStockAdjustmentsReport(String fromDate, String toDate, java.util.UUID itemId) {
        if (itemId == null) {
            String sql = """
                SELECT
                    sa.sequence_number                          AS stock_cor_no,
                    sa.created_at::DATE                         AS stock_cor_date,
                    ib.batch_number                             AS batch_no,
                    ib.expiry_date                              AS expiry_date,
                    sal.adjustment_qty                          AS quantity,
                    ib.purchase_rate                            AS purchase_rate,
                    sal.reason                                  AS reason,
                    COALESCE(u.first_name || ' ' || u.last_name, u.username) AS authorised_by,
                    ii.name                                     AS item_name
                FROM stock_adjustment sa
                JOIN stock_adjustment_lines sal ON sa.id = sal.adjustment_id
                JOIN inventory_batches ib ON sal.inventory_batch_id = ib.id
                JOIN inventory_items ii ON ib.item_id = ii.id
                LEFT JOIN users u ON sa.created_by = u.id
                WHERE sa.created_at::DATE BETWEEN ?::DATE AND ?::DATE
                ORDER BY ii.name, sa.created_at DESC
                """;
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        } else {
            String sql = """
                SELECT
                    sa.sequence_number                          AS stock_cor_no,
                    sa.created_at::DATE                         AS stock_cor_date,
                    ib.batch_number                             AS batch_no,
                    ib.expiry_date                              AS expiry_date,
                    sal.adjustment_qty                          AS quantity,
                    ib.purchase_rate                            AS purchase_rate,
                    sal.reason                                  AS reason,
                    COALESCE(u.first_name || ' ' || u.last_name, u.username) AS authorised_by,
                    ii.name                                     AS item_name
                FROM stock_adjustment sa
                JOIN stock_adjustment_lines sal ON sa.id = sal.adjustment_id
                JOIN inventory_batches ib ON sal.inventory_batch_id = ib.id
                JOIN inventory_items ii ON ib.item_id = ii.id
                LEFT JOIN users u ON sa.created_by = u.id
                WHERE sa.created_at::DATE BETWEEN ?::DATE AND ?::DATE
                  AND ii.id = ?
                ORDER BY ii.name, sa.created_at DESC
                """;
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, itemId);
        }
    }
}
