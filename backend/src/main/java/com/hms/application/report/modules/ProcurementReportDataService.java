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

        String poNo = params.get("po_no_filter") != null ? params.get("po_no_filter").toString() : null;
        String poFilter = (poNo != null && !poNo.trim().isEmpty()) ? " AND po.sequence_number = '" + poNo + "'" : "";

        String combinedFilter = supplierFilter + poFilter;

        if ("detail".equalsIgnoreCase(viewType)) {
            String sql = """
                SELECT
                    po.sequence_number                          AS po_no,
                    po.order_date                               AS po_date,
                    i.name                                      AS product_name,
                    s.name                                      AS supplier_name,
                    COALESCE(i.mrp, '0.00')                     AS mrp,
                    ROUND(pol.unit_rate / 100.0, 2)             AS purchase_price,
                    pol.quantity                                AS ordered_qty,
                    pol.received_quantity                       AS received_qty,
                    ROUND(pol.quantity * pol.unit_rate / 100.0, 2) AS total_amount,
                    po.order_status                             AS order_status,
                    pol.item_id                                 AS item_id,
                    po.notes                                    AS po_notes
                FROM purchase_orders po
                JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN purchase_orders_lines pol ON po.id = pol.order_id
                LEFT JOIN inventory_items i ON pol.item_id = i.id
                WHERE po.order_date BETWEEN ?::DATE AND ?::DATE %s
                ORDER BY po.order_date DESC, po.sequence_number DESC
                """.formatted(combinedFilter);
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        } else {
            String sql = """
                SELECT
                    po.sequence_number                          AS po_no,
                    po.order_date                               AS po_date,
                    s.name                                      AS supplier_name,
                    s.contact                                   AS supplier_contact,
                    po.order_status                             AS order_status,
                    SUM(pol.quantity)                           AS total_qty_ordered,
                    COALESCE(SUM(pol.quantity * pol.unit_rate / 100.0), 0) AS total_purchase_value,
                    u.username                                  AS user_name
                FROM purchase_orders po
                JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN purchase_orders_lines pol ON po.id = pol.order_id
                LEFT JOIN users u ON po.created_by = u.id
                WHERE po.order_date BETWEEN ?::DATE AND ?::DATE %s
                GROUP BY po.id, po.order_date, po.sequence_number, s.name, s.contact, po.order_status, u.username
                ORDER BY po.order_date DESC
                """.formatted(combinedFilter);
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        }
    }

    public List<Map<String, Object>> getGoodsReceivedReport(String fromDate, String toDate, String viewType, Map<String, Object> params) {
        String supplierId = params.get("supplier_id") != null ? params.get("supplier_id").toString() : null;
        String supplierFilter = (supplierId != null && !supplierId.trim().isEmpty()) ? " AND pr.supplier_id = '" + supplierId + "'" : "";
        String grnNo = params.get("grn_no_filter") != null ? params.get("grn_no_filter").toString() : null;
        String grnFilter = (grnNo != null && !grnNo.trim().isEmpty()) ? " AND pr.sequence_number = '" + grnNo + "'" : "";
        String combinedFilter = supplierFilter + grnFilter;

        if ("detail".equalsIgnoreCase(viewType)) {
            String sql = """
                SELECT
                    pr.sequence_number                          AS grn_no,
                    pr.created_at                               AS grn_date,
                    pr.invoice_number                           AS invoice_no,
                    s.name                                      AS supplier_name,
                    i.name                                      AS product_name,
                    prl.batch_number                            AS batch_no,
                    prl.expiry_date                             AS expiry_date,
                    ROUND(prl.maximum_retail_price, 2)          AS mrp,
                    ROUND(prl.purchase_rate, 2)                 AS purchase_price,
                    prl.quantity                                AS qty,
                    0                                           AS free_qty,
                    ROUND(prl.quantity * prl.purchase_rate, 2)  AS total_amount
                FROM purchase_receipts pr
                JOIN suppliers s ON pr.supplier_id = s.id
                LEFT JOIN purchase_orders po ON pr.purchase_order_id = po.id
                LEFT JOIN purchase_receipt_lines prl ON pr.id = prl.receipt_id
                LEFT JOIN inventory_items i ON prl.item_id = i.id
                WHERE pr.receipt_date BETWEEN ?::DATE AND ?::DATE %s
                ORDER BY pr.receipt_date DESC, pr.sequence_number DESC
                """.formatted(combinedFilter);
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        } else {
            String sql = """
                SELECT
                    pr.sequence_number                          AS grn_no,
                    pr.receipt_date                             AS received_date,
                    pr.invoice_number                           AS invoice_no,
                    pr.invoice_date                             AS invoice_date,
                    s.name                                      AS supplier_name,
                    po.sequence_number                          AS po_no,
                    SUM(prl.quantity)                           AS total_qty_received,
                    ROUND(SUM(prl.quantity * prl.purchase_rate), 2) AS grn_value,
                    u.username                                  AS user_name
                FROM purchase_receipts pr
                JOIN suppliers s ON pr.supplier_id = s.id
                LEFT JOIN purchase_orders po ON pr.purchase_order_id = po.id
                LEFT JOIN purchase_receipt_lines prl ON pr.id = prl.receipt_id
                LEFT JOIN users u ON pr.created_by = u.id
                WHERE pr.receipt_date BETWEEN ?::DATE AND ?::DATE %s
                GROUP BY pr.id, pr.sequence_number, pr.receipt_date, pr.invoice_number, pr.invoice_date, s.name, po.sequence_number, u.username
                ORDER BY pr.receipt_date DESC
                """.formatted(combinedFilter);
            return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
        }
    }

    public List<Map<String, Object>> getGoodsReturnedReport(String fromDate, String toDate, String viewType, Map<String, Object> params) {
        String supplierId = params.get("supplier_id") != null ? params.get("supplier_id").toString() : null;
        String supplierFilter = (supplierId != null && !supplierId.trim().isEmpty()) ? " AND gr.supplier_id = '" + supplierId + "'" : "";

        String returnNo = params.get("return_no_filter") != null ? params.get("return_no_filter").toString() : null;
        String returnFilter = (returnNo != null && !returnNo.trim().isEmpty()) ? " AND gr.sequence_number = '" + returnNo + "'" : "";

        String combinedFilter = supplierFilter + returnFilter;

        String sql;
        if ("detail".equalsIgnoreCase(viewType)) {
            sql = """
                SELECT
                    gr.sequence_number                          AS return_no,
                    gr.return_date                              AS return_date,
                    pr.invoice_number                           AS invoice_no,
                    pr.invoice_date                             AS invoice_date,
                    pr.sequence_number                          AS grn_no,
                    pr.receipt_date                             AS grn_date,
                    i.name                                      AS product_name,
                    ib.batch_number                             AS batch_no,
                    ib.expiry_date                              AS expiry_date,
                    ROUND(ib.maximum_retail_price, 2)           AS mrp,
                    ROUND(grl.purchase_rate, 2)                 AS purchase_price,
                    grl.quantity                                AS qty,
                    0                                           AS free_qty,
                    ROUND(grl.quantity * grl.purchase_rate, 2)  AS total_amount
                FROM goods_returns gr
                JOIN suppliers s ON gr.supplier_id = s.id
                LEFT JOIN goods_return_lines grl ON gr.id = grl.return_id
                LEFT JOIN inventory_batches ib ON grl.batch_id = ib.id
                LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
                LEFT JOIN inventory_items i ON ib.item_id = i.id
                WHERE gr.return_date BETWEEN ?::DATE AND ?::DATE %s
                ORDER BY gr.return_date DESC, gr.sequence_number DESC
                """.formatted(combinedFilter);
        } else {
            sql = """
                SELECT
                    gr.sequence_number                          AS return_no,
                    gr.return_date                              AS return_date,
                    pr.invoice_number                           AS invoice_no,
                    pr.invoice_date                             AS invoice_date,
                    gr.notes                                    AS reason_for_goods_return,
                    pr.sequence_number                          AS grn_no,
                    pr.receipt_date                             AS grn_date,
                    SUM(grl.quantity)                           AS total_qty_returned,
                    ROUND(SUM(grl.quantity * grl.purchase_rate), 2) AS total_purchase_value,
                    ROUND(SUM(grl.quantity * grl.purchase_rate), 2) AS return_value,
                    u.username                                  AS user_name
                FROM goods_returns gr
                JOIN suppliers s ON gr.supplier_id = s.id
                LEFT JOIN goods_return_lines grl ON gr.id = grl.return_id
                LEFT JOIN inventory_batches ib ON grl.batch_id = ib.id
                LEFT JOIN purchase_receipts pr ON ib.source_transaction_id = pr.id
                LEFT JOIN users u ON gr.created_by = u.id
                WHERE gr.return_date BETWEEN ?::DATE AND ?::DATE %s
                GROUP BY gr.id, gr.sequence_number, gr.return_date, pr.invoice_number, pr.invoice_date, gr.notes, pr.sequence_number, pr.receipt_date, u.username
                ORDER BY gr.return_date DESC
                """.formatted(combinedFilter);
        }
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }
}
