package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcurementReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getPurchaseOrdersReport(String fromDate, String toDate, String viewType, Map<String, Object> params) {
        String supplierId = params.get("supplier_id") != null ? params.get("supplier_id").toString() : null;
        String supplierFilter = (supplierId != null && !supplierId.trim().isEmpty()) ? " AND po.supplier_id = '" + supplierId + "'" : "";

        if ("detail".equalsIgnoreCase(viewType)) {
            String sql = """
                SELECT
                    po.order_date,
                    po.sequence_number                          AS order_number,
                    i.name                                      AS product_name,
                    s.name                                      AS supplier_name,
                    pol.quantity                                AS requested_qty,
                    pol.quantity                                AS ordered_qty,
                    po.order_status                             AS order_status
                FROM purchase_orders po
                JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN purchase_orders_lines pol ON po.id = pol.order_id
                LEFT JOIN inventory_items i ON pol.item_id = i.id
                WHERE po.order_date BETWEEN ?::DATE AND ?::DATE %s
                ORDER BY po.order_date DESC, po.sequence_number DESC
                """.formatted(supplierFilter);
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        } else {
            String sql = """
                SELECT
                    po.order_date,
                    po.sequence_number                          AS order_number,
                    s.name                                      AS supplier_name,
                    s.contact                                   AS supplier_contact,
                    po.order_status                             AS order_status,
                    COUNT(pol.id)                               AS line_count,
                    SUM(pol.quantity)                           AS total_qty_ordered,
                    ROUND(SUM(pol.quantity * pol.unit_rate), 2) AS order_value
                FROM purchase_orders po
                JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN purchase_orders_lines pol ON po.id = pol.order_id
                WHERE po.order_date BETWEEN ?::DATE AND ?::DATE %s
                GROUP BY po.id, po.order_date, po.sequence_number, s.name, s.contact, po.order_status
                ORDER BY po.order_date DESC
                """.formatted(supplierFilter);
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        }
    }

    public List<Map<String, Object>> getGoodsReceivedReport(String fromDate, String toDate, String viewType, Map<String, Object> params) {
        String supplierId = params.get("supplier_id") != null ? params.get("supplier_id").toString() : null;
        String supplierFilter = (supplierId != null && !supplierId.trim().isEmpty()) ? " AND pr.supplier_id = '" + supplierId + "'" : "";

        if ("detail".equalsIgnoreCase(viewType)) {
            String sql = """
                SELECT
                    pr.sequence_number                          AS grn_number,
                    pr.receipt_date                             AS grn_date,
                    s.name                                      AS supplier_name,
                    i.name                                      AS product_name,
                    po.sequence_number                          AS po_number,
                    prl.quantity                                AS qty,
                    ROUND(prl.purchase_rate, 2)                 AS rate,
                    ROUND(prl.maximum_retail_price, 2)          AS mrp,
                    ROUND(prl.quantity * prl.purchase_rate, 2)  AS purchase_value
                FROM purchase_receipts pr
                JOIN suppliers s ON pr.supplier_id = s.id
                LEFT JOIN purchase_orders po ON pr.purchase_order_id = po.id
                LEFT JOIN purchase_receipt_lines prl ON pr.id = prl.receipt_id
                LEFT JOIN inventory_items i ON prl.item_id = i.id
                WHERE pr.receipt_date BETWEEN ?::DATE AND ?::DATE %s
                ORDER BY pr.receipt_date DESC, pr.sequence_number DESC
                """.formatted(supplierFilter);
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        } else {
            String sql = """
                SELECT
                    pr.receipt_date                             AS received_date,
                    pr.sequence_number                          AS grn_number,
                    s.name                                      AS supplier_name,
                    po.sequence_number                          AS po_number,
                    COUNT(prl.id)                               AS line_count,
                    SUM(prl.quantity)                           AS total_qty_received,
                    ROUND(SUM(prl.quantity * prl.purchase_rate), 2) AS grn_value
                FROM purchase_receipts pr
                JOIN suppliers s ON pr.supplier_id = s.id
                LEFT JOIN purchase_orders po ON pr.purchase_order_id = po.id
                LEFT JOIN purchase_receipt_lines prl ON pr.id = prl.receipt_id
                WHERE pr.receipt_date BETWEEN ?::DATE AND ?::DATE %s
                GROUP BY pr.id, pr.receipt_date, pr.sequence_number, s.name, po.sequence_number
                ORDER BY pr.receipt_date DESC
                """.formatted(supplierFilter);
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        }
    }

    public List<Map<String, Object>> getGoodsReturnedReport(String fromDate, String toDate, String viewType, Map<String, Object> params) {
        String supplierFilter = "";
        if (params != null && params.containsKey("supplier_id") && params.get("supplier_id") != null && !params.get("supplier_id").toString().isEmpty()) {
            supplierFilter = " AND gr.supplier_id = '" + params.get("supplier_id").toString() + "' ";
        }

        String sql;
        if ("detail".equalsIgnoreCase(viewType)) {
            sql = """
                SELECT
                    gr.sequence_number                          AS return_number,
                    gr.return_date                              AS return_date,
                    s.name                                      AS supplier_name,
                    i.name                                      AS product_name,
                    grl.quantity                                AS qty,
                    ROUND(grl.purchase_rate, 2)                 AS rate,
                    ROUND(grl.quantity * grl.purchase_rate, 2)  AS return_value,
                    gr.notes                                    AS reason
                FROM goods_returns gr
                JOIN suppliers s ON gr.supplier_id = s.id
                LEFT JOIN goods_return_lines grl ON gr.id = grl.return_id
                LEFT JOIN inventory_batches ib ON grl.batch_id = ib.id
                LEFT JOIN inventory_items i ON ib.item_id = i.id
                WHERE gr.return_date BETWEEN ?::DATE AND ?::DATE %s
                ORDER BY gr.return_date DESC
                """.formatted(supplierFilter);
        } else {
            sql = """
                SELECT
                    gr.return_date                              AS return_date,
                    gr.sequence_number                          AS return_number,
                    s.name                                      AS supplier_name,
                    ''                                          AS grn_reference,
                    gr.notes                                    AS reason,
                    COUNT(grl.id)                               AS line_count,
                    SUM(grl.quantity)                           AS total_qty_returned,
                    ROUND(SUM(grl.quantity * grl.purchase_rate), 2) AS return_value
                FROM goods_returns gr
                JOIN suppliers s ON gr.supplier_id = s.id
                LEFT JOIN goods_return_lines grl ON gr.id = grl.return_id
                WHERE gr.return_date BETWEEN ?::DATE AND ?::DATE %s
                GROUP BY gr.id, gr.return_date, gr.sequence_number, s.name, gr.notes
                ORDER BY gr.return_date DESC
                """.formatted(supplierFilter);
        }
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }
}
