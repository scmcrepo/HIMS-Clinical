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
                    po.order_date                               AS po_date,
                    po.sequence_number                          AS po_no,
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
                    po.order_date                               AS po_date,
                    po.sequence_number                          AS po_no,
                    s.name                                      AS supplier_name,
                    s.contact                                   AS supplier_contact,
                    po.order_status                             AS order_status,
                    SUM(pol.quantity)                           AS total_qty_ordered
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
                    pr.sequence_number                          AS grn_no,
                    pr.receipt_date                             AS grn_date,
                    s.name                                      AS supplier_name,
                    i.name                                      AS product_name,
                    po.sequence_number                          AS po_no,
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
                    pr.sequence_number                          AS grn_no,
                    s.name                                      AS supplier_name,
                    po.sequence_number                          AS po_no,
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
                    gr.sequence_number                          AS return_no,
                    gr.return_date                              AS return_date,
                    s.name                                      AS supplier_name,
                    i.name                                      AS product_name,
                    pr.sequence_number                          AS grn_no,
                    grl.quantity                                AS qty,
                    ROUND(grl.purchase_rate, 2)                 AS rate,
                    ROUND(grl.quantity * grl.purchase_rate, 2)  AS return_value,
                    gr.notes                                    AS reason
                FROM goods_returns gr
                JOIN suppliers s ON gr.supplier_id = s.id
                LEFT JOIN goods_return_lines grl ON gr.id = grl.return_id
                LEFT JOIN inventory_batches ib ON grl.batch_id = ib.id
                LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
                LEFT JOIN inventory_items i ON ib.item_id = i.id
                WHERE gr.return_date BETWEEN ?::DATE AND ?::DATE %s
                ORDER BY gr.return_date DESC
                """.formatted(supplierFilter);
        } else {
            sql = """
                SELECT
                    gr.return_date                              AS return_date,
                    gr.sequence_number                          AS return_no,
                    s.name                                      AS supplier_name,
                    pr.sequence_number                          AS grn_no,
                    gr.notes                                    AS reason,
                    SUM(grl.quantity)                           AS total_qty_returned,
                    ROUND(SUM(grl.quantity * grl.purchase_rate), 2) AS return_value
                FROM goods_returns gr
                JOIN suppliers s ON gr.supplier_id = s.id
                LEFT JOIN goods_return_lines grl ON gr.id = grl.return_id
                LEFT JOIN inventory_batches ib ON grl.batch_id = ib.id
                LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
                WHERE gr.return_date BETWEEN ?::DATE AND ?::DATE %s
                GROUP BY gr.id, gr.return_date, gr.sequence_number, s.name, gr.notes, pr.sequence_number
                ORDER BY gr.return_date DESC
                """.formatted(supplierFilter);
        }
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }
}
