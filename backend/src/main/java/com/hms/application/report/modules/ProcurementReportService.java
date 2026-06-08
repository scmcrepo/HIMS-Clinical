package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProcurementReportService extends BaseReportService {

    private final ProcurementReportDataService procurementReportDataService;
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    public ProcurementReportService(ReportEngine reportEngine, ProcurementReportDataService procurementReportDataService) {
        super(reportEngine);
        this.procurementReportDataService = procurementReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "purchase_orders_report", "description", "Purchase Order Summary Report", "category", "Procurement"),
        Map.of("name", "goods_received_report", "description", "Goods Received Summary Report", "category", "Procurement"),
        Map.of("name", "goods_returned_report", "description", "Goods Returned Summary Report", "category", "Procurement")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        
        List<Map<String, Object>> PO_PARAMS = new ArrayList<>(DATE_RANGE_PARAMS);
        PO_PARAMS.add(Map.of("name", "supplier_id", "description", "Supplier", "type", "SUPPLIER", "required", false));
        PO_PARAMS.add(Map.of("name", "report_view_type", "description", "Report", "type", "REPORT_VIEW_TYPE", "required", true, "defaultValue", "summary"));

        for (Map<String, String> r : CATALOGUE) {
            if (r.get("name").equals("purchase_orders_report") || r.get("name").equals("goods_received_report") || r.get("name").equals("goods_returned_report")) {
                m.put(r.get("name"), PO_PARAMS);
            } else {
                m.put(r.get("name"), DATE_RANGE_PARAMS);
            }
        }
        PARAMS = Collections.unmodifiableMap(m);
    }

    @Override
    public List<Map<String, String>> getAvailableReports() {
        return CATALOGUE;
    }

    @Override
    public Map<String, Object> getReportInfo(String reportName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("reportName", reportName);
        CATALOGUE.stream()
            .filter(r -> r.get("name").equals(reportName))
            .findFirst()
            .ifPresent(meta -> {
                info.put("description", meta.get("description"));
                info.put("category",    meta.get("category"));
            });
        info.put("parameters", PARAMS.getOrDefault(reportName, List.of()));
        return info;
    }

    @Override
    public List<Map<String, Object>> executeDataQuery(String reportName, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        return switch (reportName) {
            case "purchase_orders_report" -> {
                String viewType = params.getOrDefault("report_view_type", "summary").toString();
                yield procurementReportDataService.getPurchaseOrdersReport(from, to, viewType, params);
            }
            case "goods_received_report" -> {
                String viewType = params.getOrDefault("report_view_type", "summary").toString();
                yield procurementReportDataService.getGoodsReceivedReport(from, to, viewType, params);
            }
            case "goods_returned_report" -> {
                String viewType = params.getOrDefault("report_view_type", "summary").toString();
                yield procurementReportDataService.getGoodsReturnedReport(from, to, viewType, params);
            }
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if ("purchase_orders_report".equals(reportName)) {
            String viewType = params.getOrDefault("report_view_type", "summary").toString();
            if ("detail".equalsIgnoreCase(viewType)) {
                return buildPurchaseOrdersDetailHtml(rows, params);
            } else {
                return buildPurchaseOrdersSummaryHtml(rows, params);
            }
        }
        if ("goods_received_report".equals(reportName)) {
            String viewType = params.getOrDefault("report_view_type", "summary").toString();
            if ("detail".equalsIgnoreCase(viewType)) {
                return buildGoodsReceivedDetailHtml(rows, params);
            } else {
                return buildGoodsReceivedSummaryHtml(rows, params);
            }
        }
        if ("goods_returned_report".equals(reportName)) {
            String viewType = params.getOrDefault("report_view_type", "summary").toString();
            if ("detail".equalsIgnoreCase(viewType)) {
                return buildGoodsReturnedDetailHtml(rows, params);
            } else {
                return buildGoodsReturnedSummaryHtml(rows, params);
            }
        }
        return null;
    }

    private String buildPurchaseOrdersSummaryHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }
        String periodStr = fromDate.equals(toDate) 
            ? "on " + toDate 
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='text-align: left; margin-bottom: 20px;'>");
        sb.append("  <h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Purchase Order Summary Report</h2>");
        sb.append("  <div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Purchase Order ").append(periodStr).append("</div>");
        sb.append("</div>");

        sb.append("  <div class='summary'>");
        sb.append("    <strong>Purchase Orders Report</strong> &nbsp;|&nbsp; ").append(rows.size()).append(" record(s)");
        sb.append("  </div>");

        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>PO No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>PO Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Supplier Name</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Supplier Contact</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Order Status</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Total Qty Ordered</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Total Purchase Value</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>User Name</th>");
        sb.append("</tr></thead><tbody>");

        long totalQty = 0;
        long totalVal = 0;
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='8' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                String poNo = reportEngine.str(r, "po_no");
                Object poDateVal = r.get("po_date");
                String poDate = (poDateVal instanceof java.sql.Date || poDateVal instanceof java.time.LocalDate)
                    ? reportEngine.formatDateValue(poDateVal)
                    : reportEngine.formatGeneralValue(poDateVal);
                String supplierName = reportEngine.str(r, "supplier_name");
                String supplierContact = reportEngine.str(r, "supplier_contact");
                String orderStatus = reportEngine.str(r, "order_status");
                long qty = Math.round(reportEngine.doubleVal(r.get("total_qty_ordered")));
                totalQty += qty;
                long purchaseVal = Math.round(reportEngine.doubleVal(r.get("total_purchase_value")));
                totalVal += purchaseVal;
                String userName = reportEngine.str(r, "user_name");

                sb.append("<tr>");
                sb.append("<td style='padding:6px 10px;'>");
                sb.append("<a href='#' class='po-link' data-po-no='").append(reportEngine.escHtml(poNo)).append("' style='color:#4b5563;text-decoration:underline;font-weight:600;cursor:pointer;'>")
                  .append(reportEngine.escHtml(poNo)).append("</a>");
                sb.append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(poDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(supplierName)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(supplierContact)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(orderStatus)).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(qty).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(purchaseVal).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(userName)).append("</td>");
                sb.append("</tr>");
            }
            
            // Grand Total Row
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td colspan='6' style='padding:8px 10px;text-align:right;'>Grand Total</td>");
            sb.append("<td style='padding:8px 10px;text-align:right;'>").append(totalVal).append("</td>");
            sb.append("<td></td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildPurchaseOrdersDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }
        String periodStr = fromDate.equals(toDate) 
            ? "on " + toDate 
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; padding-bottom: 8px; border-bottom: 2px solid #e2e8f0;'>");
        sb.append("  <div>");
        sb.append("    <h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Purchase Order Detail Report</h2>");
        sb.append("    <div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Purchase Order ").append(periodStr).append("</div>");
        sb.append("  </div>");
        
        String poNoFilter = params.getOrDefault("po_no_filter", "").toString();
        if (!poNoFilter.trim().isEmpty()) {
            sb.append("  <button class='po-back-btn' style='padding:6px 12px;background:#525252;color:#fff;border:none;border-radius:6px;cursor:pointer;font-weight:600;font-size:13px;display:inline-flex;align-items:center;gap:6px;box-shadow:0 1px 3px rgba(0,0,0,0.1);'>");
            sb.append("    <svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><line x1='19' y1='12' x2='5' y2='12'></line><polyline points='12 19 5 12 12 5'></polyline></svg>");
            sb.append("    Back");
            sb.append("  </button>");
        }
        sb.append("</div>");

        sb.append("<div class='summary'>");
        sb.append("<strong>Purchase Orders Detail Report</strong> &nbsp;|&nbsp; ").append(rows.size()).append(" record(s)");
        sb.append("</div>");

        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>PO No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>PO Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Supplier Name</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Product Name</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>MRP</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Purchase Price</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Ordered Qty</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Received Qty</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Total Amount</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Order Status</th>");
        sb.append("</tr></thead><tbody>");

        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='10' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            long totalOrdered = 0;
            long totalReceived = 0;
            long totalAmountVal = 0;

            for (Map<String, Object> r : rows) {
                String poNo = reportEngine.str(r, "po_no");
                Object poDateVal = r.get("po_date");
                String poDate = (poDateVal instanceof java.sql.Date || poDateVal instanceof java.time.LocalDate)
                    ? reportEngine.formatDateValue(poDateVal)
                    : reportEngine.formatGeneralValue(poDateVal);
                String productName = reportEngine.str(r, "product_name");
                String supplierName = reportEngine.str(r, "supplier_name");
                
                double mrpVal = reportEngine.doubleVal(r.get("mrp"));
                String poNotes = reportEngine.str(r, "po_notes");
                String itemId = reportEngine.str(r, "item_id");
                if (poNotes != null && !poNotes.isBlank() && itemId != null && !itemId.isBlank()) {
                    try {
                        List<Map<String, Object>> parsedLines = OBJECT_MAPPER.readValue(
                            poNotes,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
                        );
                        for (Map<String, Object> line : parsedLines) {
                            String lineItemId = (String) line.get("itemId");
                            if (itemId.equals(lineItemId) && line.containsKey("mrp")) {
                                mrpVal = reportEngine.doubleVal(line.get("mrp"));
                                break;
                            }
                        }
                    } catch (Exception e) {
                        // fallback to default catalog mrp
                    }
                }
                
                long mrp = Math.round(mrpVal);
                long purchasePrice = Math.round(reportEngine.doubleVal(r.get("purchase_price")));
                long orderedQty = Math.round(reportEngine.doubleVal(r.get("ordered_qty")));
                long receivedQty = Math.round(reportEngine.doubleVal(r.get("received_qty")));
                long totalAmount = Math.round(reportEngine.doubleVal(r.get("total_amount")));
                String orderStatus = reportEngine.str(r, "order_status");

                totalOrdered += orderedQty;
                totalReceived += receivedQty;
                totalAmountVal += totalAmount;

                sb.append("<tr>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(poNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(poDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(supplierName)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(productName)).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(mrp).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(purchasePrice).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(orderedQty).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(receivedQty).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(totalAmount).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(orderStatus)).append("</td>");
                sb.append("</tr>");
            }

            // Grand Total Row
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td colspan='8' style='padding:8px 10px;text-align:right;'>Grand Total</td>");
            sb.append("<td style='padding:8px 10px;text-align:right;'>").append(totalAmountVal).append("</td>");
            sb.append("<td></td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildGoodsReceivedSummaryHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }
        String periodStr = fromDate.equals(toDate) 
            ? "on " + toDate 
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='text-align: left; margin-bottom: 20px;'>");
        sb.append("  <h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Goods Received Summary Report</h2>");
        sb.append("  <div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Goods Received ").append(periodStr).append("</div>");
        sb.append("</div>");

        sb.append("  <div class='summary'>");
        sb.append("    <strong>Goods Received Report</strong> &nbsp;|&nbsp; ").append(rows.size()).append(" record(s)");
        sb.append("  </div>");

        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>GRN No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>GRN Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Invoice No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Invoice Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Supplier Name</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>PO No</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Total Qty Received</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>GRN Value</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>User Name</th>");
        sb.append("</tr></thead><tbody>");

        long totalQty = 0;
        long totalVal = 0;
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='9' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                String grnNo = reportEngine.str(r, "grn_no");
                Object receivedDateVal = r.get("received_date");
                String receivedDate = receivedDateVal != null ? reportEngine.formatDateValue(receivedDateVal) : "";
                
                String invoiceNo = reportEngine.str(r, "invoice_no");
                Object invoiceDateVal = r.get("invoice_date");
                String invoiceDate = invoiceDateVal != null ? reportEngine.formatDateValue(invoiceDateVal) : "";
                
                String supplierName = reportEngine.str(r, "supplier_name");
                String poNoVal = reportEngine.str(r, "po_no");
                String poNo = (poNoVal == null || poNoVal.isBlank()) ? "Direct Sales" : poNoVal;
                long qty = Math.round(reportEngine.doubleVal(r.get("total_qty_received")));
                totalQty += qty;
                long grnValue = Math.round(reportEngine.doubleVal(r.get("grn_value")));
                totalVal += grnValue;
                String userName = reportEngine.str(r, "user_name");

                 sb.append("<tr>");
                sb.append("<td style='padding:6px 10px;'><a href='#' class='grn-link' data-grn-no='")
                  .append(reportEngine.escHtml(grnNo)).append("' style='color:#4b5563;text-decoration:underline;font-weight:600;cursor:pointer;'>")
                  .append(reportEngine.escHtml(grnNo)).append("</a></td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(receivedDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(invoiceNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(invoiceDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(supplierName)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(poNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(qty).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(grnValue).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(userName)).append("</td>");
                sb.append("</tr>");
            }
            
            // Grand Total Row
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td colspan='7' style='padding:8px 10px;text-align:right;'>Grand Total</td>");
            sb.append("<td style='padding:8px 10px;text-align:right;'>").append(totalVal).append("</td>");
            sb.append("<td></td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildGoodsReceivedDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }
        String periodStr = fromDate.equals(toDate) 
            ? "on " + toDate 
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; padding-bottom: 8px; border-bottom: 2px solid #e2e8f0;'>");
        sb.append("  <div>");
        sb.append("    <h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Goods Received Detail Report</h2>");
        sb.append("    <div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Goods Received ").append(periodStr).append("</div>");
        sb.append("  </div>");

        String grnFilter = reportEngine.str(params, "grn_no_filter");
        if (grnFilter != null && !grnFilter.trim().isEmpty()) {
            sb.append("  <button class='grn-back-btn' style='padding:6px 12px;background:#525252;color:#fff;border:none;border-radius:6px;cursor:pointer;font-weight:600;font-size:13px;display:inline-flex;align-items:center;gap:6px;box-shadow:0 1px 3px rgba(0,0,0,0.1);'>");
            sb.append("    <svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><line x1='19' y1='12' x2='5' y2='12'></line><polyline points='12 19 5 12 12 5'></polyline></svg>");
            sb.append("    Back");
            sb.append("  </button>");
        }
        sb.append("</div>");

        sb.append("  <div class='summary'>");
        sb.append("    <strong>Goods Received Report</strong> &nbsp;|&nbsp; ").append(rows.size()).append(" record(s)");
        sb.append("  </div>");

        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>GRN No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>GRN Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Invoice No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Supplier Name</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Product Name</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Batch No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Expiry Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>MRP</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Purchase Price</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Qty</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Free Qty</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Total Amount</th>");
        sb.append("</tr></thead><tbody>");

        long totalAmount = 0;
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='12' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                String grnNo = reportEngine.str(r, "grn_no");
                Object grnDateVal = r.get("grn_date");
                String grnDate = grnDateVal != null ? reportEngine.formatDateValue(grnDateVal) : "";
                String invoiceNo = reportEngine.str(r, "invoice_no");
                String supplierName = reportEngine.str(r, "supplier_name");
                String productName = reportEngine.str(r, "product_name");
                String batchNo = reportEngine.str(r, "batch_no");
                Object expiryDateVal = r.get("expiry_date");
                String expiryDate = expiryDateVal != null ? reportEngine.formatDateValue(expiryDateVal) : "";
                long mrp = Math.round(reportEngine.doubleVal(r.get("mrp")));
                long purchasePrice = Math.round(reportEngine.doubleVal(r.get("purchase_price")));
                long qty = Math.round(reportEngine.doubleVal(r.get("qty")));
                long freeQty = Math.round(reportEngine.doubleVal(r.get("free_qty")));
                long amt = Math.round(reportEngine.doubleVal(r.get("total_amount")));
                totalAmount += amt;

                sb.append("<tr>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(grnNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(grnDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(invoiceNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(supplierName)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(productName)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(batchNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(expiryDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(mrp).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(purchasePrice).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(qty).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(freeQty).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(amt).append("</td>");
                sb.append("</tr>");
            }
            
            // Grand Total Row
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td colspan='11' style='padding:8px 10px;text-align:right;'>Grand Total</td>");
            sb.append("<td style='padding:8px 10px;text-align:right;'>").append(totalAmount).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildGoodsReturnedSummaryHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }
        String periodStr = fromDate.equals(toDate) 
            ? "on " + toDate 
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='text-align: left; margin-bottom: 20px;'>");
        sb.append("  <h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Goods Returned Summary Report</h2>");
        sb.append("  <div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Goods Returned ").append(periodStr).append("</div>");
        sb.append("</div>");

        sb.append("  <div class='summary'>");
        sb.append("    <strong>Goods Returned Report</strong> &nbsp;|&nbsp; ").append(rows.size()).append(" record(s)");
        sb.append("  </div>");

        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Return No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Return Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Invoice No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Invoice Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Reason for Goods Return</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>GRN No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>GRN Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Total Purchase Value</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>User Name</th>");
        sb.append("</tr></thead><tbody>");

        long totalVal = 0;
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='9' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                String returnNo = reportEngine.str(r, "return_no");
                Object returnDateVal = r.get("return_date");
                String returnDate = returnDateVal != null ? reportEngine.formatDateValue(returnDateVal) : "";
                
                String invoiceNo = reportEngine.str(r, "invoice_no");
                Object invoiceDateVal = r.get("invoice_date");
                String invoiceDate = invoiceDateVal != null ? reportEngine.formatDateValue(invoiceDateVal) : "";
                
                String reason = reportEngine.str(r, "reason_for_goods_return");
                String grnNo = reportEngine.str(r, "grn_no");
                Object grnDateVal = r.get("grn_date");
                String grnDate = grnDateVal != null ? reportEngine.formatDateValue(grnDateVal) : "";
                
                long purchaseVal = Math.round(reportEngine.doubleVal(r.get("total_purchase_value")));
                totalVal += purchaseVal;
                String userName = reportEngine.str(r, "user_name");

                sb.append("<tr>");
                sb.append("<td style='padding:6px 10px;'><a href='#' class='return-link' data-return-no='")
                  .append(reportEngine.escHtml(returnNo)).append("' style='color:#4b5563;text-decoration:underline;font-weight:600;cursor:pointer;'>")
                  .append(reportEngine.escHtml(returnNo)).append("</a></td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(returnDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(invoiceNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(invoiceDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(reason)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(grnNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(grnDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(purchaseVal).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(userName)).append("</td>");
                sb.append("</tr>");
            }
            
            // Grand Total Row
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td colspan='7' style='padding:8px 10px;text-align:right;'>Grand Total</td>");
            sb.append("<td style='padding:8px 10px;text-align:right;'>").append(totalVal).append("</td>");
            sb.append("<td></td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildGoodsReturnedDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }
        String periodStr = fromDate.equals(toDate) 
            ? "on " + toDate 
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; padding-bottom: 8px; border-bottom: 2px solid #e2e8f0;'>");
        sb.append("  <div>");
        sb.append("    <h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Goods Returned Detail Report</h2>");
        sb.append("    <div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Goods Returned ").append(periodStr).append("</div>");
        sb.append("  </div>");

        String returnFilter = reportEngine.str(params, "return_no_filter");
        if (returnFilter != null && !returnFilter.trim().isEmpty()) {
            sb.append("  <button class='return-back-btn' style='padding:6px 12px;background:#525252;color:#fff;border:none;border-radius:6px;cursor:pointer;font-weight:600;font-size:13px;display:inline-flex;align-items:center;gap:6px;box-shadow:0 1px 3px rgba(0,0,0,0.1);'>");
            sb.append("    <svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><line x1='19' y1='12' x2='5' y2='12'></line><polyline points='12 19 5 12 12 5'></polyline></svg>");
            sb.append("    Back");
            sb.append("  </button>");
        }
        sb.append("</div>");

        sb.append("  <div class='summary'>");
        sb.append("    <strong>Goods Returned Detail Report</strong> &nbsp;|&nbsp; ").append(rows.size()).append(" record(s)");
        sb.append("  </div>");

        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Return No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Return Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Invoice No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Invoice Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>GRN No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>GRN Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Product Name</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Batch No</th>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>Expiry Date</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>MRP</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Purchase Price</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Qty</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Free Qty</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;'>Total Amount</th>");
        sb.append("</tr></thead><tbody>");

        long totalVal = 0;
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='14' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                String returnNo = reportEngine.str(r, "return_no");
                Object returnDateVal = r.get("return_date");
                String returnDate = returnDateVal != null ? reportEngine.formatDateValue(returnDateVal) : "";
                
                String invoiceNo = reportEngine.str(r, "invoice_no");
                Object invoiceDateVal = r.get("invoice_date");
                String invoiceDate = invoiceDateVal != null ? reportEngine.formatDateValue(invoiceDateVal) : "";
                
                String grnNo = reportEngine.str(r, "grn_no");
                Object grnDateVal = r.get("grn_date");
                String grnDate = grnDateVal != null ? reportEngine.formatDateValue(grnDateVal) : "";
                
                String productName = reportEngine.str(r, "product_name");
                String batchNo = reportEngine.str(r, "batch_no");
                Object expiryDateVal = r.get("expiry_date");
                String expiryDate = expiryDateVal != null ? reportEngine.formatDateValue(expiryDateVal) : "";
                
                long mrp = Math.round(reportEngine.doubleVal(r.get("mrp")));
                long purchasePrice = Math.round(reportEngine.doubleVal(r.get("purchase_price")));
                long qty = Math.round(reportEngine.doubleVal(r.get("qty")));
                long freeQty = Math.round(reportEngine.doubleVal(r.get("free_qty")));
                long totalAmount = Math.round(reportEngine.doubleVal(r.get("total_amount")));
                totalVal += totalAmount;

                sb.append("<tr>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(returnNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(returnDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(invoiceNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(invoiceDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(grnNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(grnDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(productName)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(batchNo)).append("</td>");
                sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(expiryDate)).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(mrp).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(purchasePrice).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(qty).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(freeQty).append("</td>");
                sb.append("<td style='padding:6px 10px;text-align:right;'>").append(totalAmount).append("</td>");
                sb.append("</tr>");
            }
            
            // Grand Total Row
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td colspan='13' style='padding:8px 10px;text-align:right;'>Grand Total</td>");
            sb.append("<td style='padding:8px 10px;text-align:right;'>").append(totalVal).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }
}
