package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class InventoryReportService extends BaseReportService {

    private final InventoryReportDataService inventoryReportDataService;

    public InventoryReportService(ReportEngine reportEngine, InventoryReportDataService inventoryReportDataService) {
        super(reportEngine);
        this.inventoryReportDataService = inventoryReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "current_stock", "description", "Item-wise Current Stock", "category", "Inventory"),
        Map.of("name", "expired_items", "description", "Expiry Stock Report", "category", "Inventory"),
        Map.of("name", "items_expiring_month", "description", "Nearing Expiry Stock Report", "category", "Inventory"),
        Map.of("name", "slow_moving_items", "description", "Items Not Sold in Previous 30 Days", "category", "Inventory"),
        Map.of("name", "zero_stock_items", "description", "Nil Stock Report", "category", "Inventory"),
        Map.of("name", "stock_and_nil_stock", "description", "Stock and Nil Stock Report", "category", "Inventory"),
        Map.of("name", "scheduled_drug_sales", "description", "Date / Item-wise Scheduled Drug Sales", "category", "Inventory"),
        Map.of("name", "below_reorder_level", "description", "Items with Stock Below Reorder Level", "category", "Inventory"),
        Map.of("name", "stock_adjustments", "description", "Date / Item-wise Stock Adjustments", "category", "Inventory")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        m.put("current_stock", List.of(
            param("dept_id", "DEPARTMENT", true, "", "Department"),
            param("item_id", "ITEM", false, "", "Item")
        ));
        m.put("expired_items", List.of(
            param("to_date", "DATE", true, "", "Date"),
            param("dept_id", "DEPARTMENT", true, "", "Department")
        ));
        m.put("items_expiring_month", List.of(
            param("dept_id", "DEPARTMENT", true, "", "Department"),
            param("month_interval", "MONTH_INTERVAL", true, "1", "Month Interval")
        ));
        m.put("slow_moving_items", List.of(
            param("dept_id", "DEPARTMENT", true, "", "Department")
        ));
        m.put("zero_stock_items", List.of(
            param("to_date", "DATE", true, "", "Date"),
            param("dept_id", "DEPARTMENT", true, "", "Department")
        ));
        m.put("stock_and_nil_stock", List.of(
            param("to_date", "DATE", true, "", "Date"),
            param("dept_id", "DEPARTMENT", true, "", "Department")
        ));
        m.put("below_reorder_level", List.of());

        m.put("scheduled_drug_sales", List.of(
            param("from_date", "DATE", true, "", "From Date"),
            param("to_date", "DATE", true, "", "To Date"),
            param("scheduled_drug_type", "SCHEDULED_DRUG_TYPE", true, "H", "Scheduled Drug Type")
        ));
        m.put("stock_adjustments", List.of(
            param("from_date", "DATE", true, "", "From Date"),
            param("to_date", "DATE", true, "", "To Date"),
            param("item_id", "ITEM", false, "", "Item")
        ));
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
            case "current_stock" -> inventoryReportDataService.getCurrentStockReport(
                reportEngine.uuid(params, "dept_id"),
                reportEngine.uuid(params, "item_id")
            );
            case "expired_items" -> inventoryReportDataService.getExpiredItemsReport(
                reportEngine.uuid(params, "dept_id"),
                reportEngine.dateStr(params, "to_date")
            );
            case "items_expiring_month" -> inventoryReportDataService.getItemsExpiringWithinMonth(
                reportEngine.uuid(params, "dept_id"),
                reportEngine.str(params, "month_interval")
            );
            case "slow_moving_items" -> inventoryReportDataService.getSlowMovingItemsReport(
                reportEngine.uuid(params, "dept_id")
            );
            case "zero_stock_items" -> inventoryReportDataService.getNilStockReport(
                reportEngine.uuid(params, "dept_id")
            );
            case "stock_and_nil_stock" -> List.of(Map.of("dummy", "value"));
            case "scheduled_drug_sales" -> inventoryReportDataService.getScheduledDrugSalesReport(
                from,
                to,
                reportEngine.str(params, "scheduled_drug_type")
            );
            case "below_reorder_level" -> inventoryReportDataService.getItemsBelowReorderLevel();
            case "stock_adjustments" -> inventoryReportDataService.getStockAdjustmentsReport(
                from,
                to,
                reportEngine.uuid(params, "item_id")
            );
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if ("current_stock".equals(reportName)) {
            if (rows.isEmpty()) {
                return "<p style='padding:16px;color:#64748b;font-family:sans-serif'>No data found for the selected parameters.</p>";
            }
            String genericHtml = reportEngine.executeAsHtml(reportName, rows, params);
            int tableIndex = genericHtml.indexOf("<table>");
            String tableHtml = (tableIndex != -1) ? genericHtml.substring(tableIndex) : genericHtml;

            String currentDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String headerHtml = 
                "<div style='text-align: left; margin-bottom: 20px; font-family: sans-serif;'>" +
                "  <h2 style='margin: 0; color: #1e40af; font-size: 22px; font-weight: 700;'>Current Stock Report</h2>" +
                "  <div style='margin-top: 6px; color: #475569; font-size: 13px; font-weight: 500;'>Current Stock as on " + currentDate + "</div>" +
                "</div>" +
                "<div style='margin-bottom: 12px; font-size: 11px; color: #64748b; font-family: sans-serif;'>" +
                "  Total Records: <strong>" + rows.size() + "</strong>" +
                "</div>";

            return headerHtml + tableHtml;
        }
        if ("expired_items".equals(reportName)) {
            if (rows.isEmpty()) {
                return "<p style='padding:16px;color:#64748b;font-family:sans-serif'>No data found for the selected parameters.</p>";
            }
            String genericHtml = reportEngine.executeAsHtml(reportName, rows, params);
            int tableIndex = genericHtml.indexOf("<table>");
            String tableHtml = (tableIndex != -1) ? genericHtml.substring(tableIndex) : genericHtml;

            String displayDate = "";
            try {
                String toDate = reportEngine.dateStr(params, "to_date");
                java.time.LocalDate date = java.time.LocalDate.parse(toDate);
                displayDate = date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            } catch (Exception e) {
                displayDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            }

            String headerHtml = 
                "<div style='text-align: left; margin-bottom: 20px; font-family: sans-serif;'>" +
                "  <h2 style='margin: 0; color: #1e293b; font-size: 22px; font-weight: 700;'>Expiry Stock Report</h2>" +
                "  <div style='margin-top: 6px; color: #475569; font-size: 13px; font-weight: 500;'>Products Expiry Report on or before " + displayDate + "</div>" +
                "</div>" +
                "<div style='margin-bottom: 12px; font-size: 11px; color: #64748b; font-family: sans-serif;'>" +
                "  Total Records: <strong>" + rows.size() + "</strong>" +
                "</div>";

            return headerHtml + tableHtml;
        }
        if ("items_expiring_month".equals(reportName)) {
            if (rows.isEmpty()) {
                return "<p style='padding:16px;color:#64748b;font-family:sans-serif'>No data found for the selected parameters.</p>";
            }
            String genericHtml = reportEngine.executeAsHtml(reportName, rows, params);
            int tableIndex = genericHtml.indexOf("<table>");
            String tableHtml = (tableIndex != -1) ? genericHtml.substring(tableIndex) : genericHtml;

            String headerHtml = 
                "<div style='text-align: left; margin-bottom: 20px; font-family: sans-serif;'>" +
                "  <h2 style='margin: 0; color: #1e293b; font-size: 22px; font-weight: 700;'>Nearing Expiry Report</h2>" +
                "  <div style='margin-top: 6px; color: #475569; font-size: 13px; font-weight: 500;'>Nearing Expiry Report</div>" +
                "</div>" +
                "<div style='margin-bottom: 12px; font-size: 11px; color: #64748b; font-family: sans-serif;'>" +
                "  Total Records: <strong>" + rows.size() + "</strong>" +
                "</div>";

            return headerHtml + tableHtml;
        }
        if ("zero_stock_items".equals(reportName) || "stock_and_nil_stock".equals(reportName)) {
            UUID deptId = reportEngine.uuid(params, "dept_id");
            String toDateStr = reportEngine.dateStr(params, "to_date");
            if (toDateStr == null || toDateStr.trim().isEmpty()) {
                toDateStr = reportEngine.dateStr(params, "from_date");
            }
            
            String displayDate = "";
            try {
                java.time.LocalDate date = java.time.LocalDate.parse(toDateStr);
                displayDate = date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            } catch (Exception e) {
                displayDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            }

            List<Map<String, Object>> stockRows = inventoryReportDataService.getCurrentStockReport(deptId, null);
            List<Map<String, Object>> nilStockRows = inventoryReportDataService.getNilStockReport(deptId);

            StringBuilder html = new StringBuilder();
            html.append("<style>")
                .append("body { font-family: 'Segoe UI', sans-serif; color: #1e293b; margin: 0; }")
                .append("table { border-collapse: collapse; width: 100%; font-size: 12px; margin-bottom: 25px; }")
                .append("thead tr { background: #1e40af; color: #fff; }")
                .append("th { padding: 8px 10px; text-align: left; white-space: nowrap; font-weight: 600; }")
                .append("td { padding: 6px 10px; border-bottom: 1px solid #e2e8f0; white-space: nowrap; text-align: left; }")
                .append("tr:nth-child(even) { background: #f8fafc; }")
                .append(".report-section { margin-bottom: 30px; }")
                .append(".report-title { margin: 0; color: #1e40af; font-size: 20px; font-weight: 700; }")
                .append(".report-subtitle { margin-top: 4px; margin-bottom: 12px; color: #475569; font-size: 13px; font-weight: 500; }")
                .append(".error-msg-row { color: #ef4444; font-weight: bold; text-align: center; font-size: 14px; padding: 12px; }")
                .append("</style>");

            // 1. Stock Report Section
            html.append("<div class='report-section'>");
            html.append("  <h2 class='report-title'>Stock Report</h2>");
            html.append("  <div class='report-subtitle'>Stock for ").append(displayDate).append("</div>");
            
            html.append("  <table><thead><tr>");
            List<String> headers = List.of(
                "Product Name", "Batch No", "Expiry Date", "Qty", "Purchase Value",
                "CGST%", "CGST Amt", "SGST%", "SGST Amt", "IGST%", "IGST Amt", "Total Value", "MRP", "Supplier"
            );
            for (String h : headers) {
                html.append("<th>").append(h).append("</th>");
            }
            html.append("</tr></thead><tbody>");
            
            if (stockRows.isEmpty()) {
                html.append("<tr><td colspan='14' class='error-msg-row'>No Record Found !!! There is no Stock for ").append(displayDate).append("</td></tr>");
            } else {
                for (Map<String, Object> row : stockRows) {
                    html.append("<tr>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("Product Name")))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("Batch No")))).append("</td>");
                    
                    String expDate = reportEngine.formatGeneralValue(row.get("Expiry Date"));
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(expDate);
                        expDate = d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) {
                        // ignore
                    }
                    html.append("<td>").append(expDate).append("</td>");
                    
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("Qty"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("Purchase Value"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("CGST %"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("CGST Amt"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("SGST %"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("SGST Amt"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("IGST %"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("IGST Amt"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("Total Value"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("MRP"))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("Supplier")))).append("</td>");
                    html.append("</tr>");
                }
            }
            html.append("</tbody></table>");
            html.append("</div>");

            // 2. Nil Stock Report Section
            html.append("<div class='report-section'>");
            html.append("  <h2 class='report-title'>Nil Stock Report</h2>");
            
            html.append("  <table style='width: 50%;'><thead><tr>");
            html.append("<th>Product Name</th>");
            html.append("</tr></thead><tbody>");
            
            if (nilStockRows.isEmpty()) {
                html.append("<tr><td colspan='1' class='error-msg-row'>No Record Found !!! There is no Nil Stock for ").append(displayDate).append("</td></tr>");
            } else {
                for (Map<String, Object> row : nilStockRows) {
                    html.append("<tr>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("Product Name")))).append("</td>");
                    html.append("</tr>");
                }
            }
            html.append("</tbody></table>");
            html.append("</div>");

            return html.toString();
        }

        if ("scheduled_drug_sales".equals(reportName)) {
            String fromDateStr = reportEngine.dateStr(params, "from_date");
            String toDateStr = reportEngine.dateStr(params, "to_date");
            
            String displayDate = "";
            try {
                java.time.LocalDate fromDate = java.time.LocalDate.parse(fromDateStr);
                java.time.LocalDate toDate = java.time.LocalDate.parse(toDateStr);
                if (fromDate.equals(toDate)) {
                    displayDate = fromDate.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                } else {
                    displayDate = fromDate.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")) + " to " +
                                  toDate.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }
            } catch (Exception e) {
                displayDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            }

            StringBuilder html = new StringBuilder();
            html.append("<style>")
                .append("body { font-family: 'Segoe UI', sans-serif; color: #1e293b; margin: 0; }")
                .append("table { border-collapse: collapse; width: 100%; font-size: 12px; margin-bottom: 25px; }")
                .append("thead tr { background: #1e40af; color: #fff; }")
                .append("th { padding: 8px 10px; text-align: left; white-space: nowrap; font-weight: 600; }")
                .append("td { padding: 6px 10px; border-bottom: 1px solid #e2e8f0; white-space: nowrap; text-align: left; }")
                .append("tr:nth-child(even) { background: #f8fafc; }")
                .append(".report-title { margin: 0; color: #1e40af; font-size: 20px; font-weight: 700; margin-bottom: 15px; }")
                .append(".error-msg-row { color: #ef4444; font-weight: bold; text-align: center; font-size: 14px; padding: 12px; }")
                .append("</style>");

            html.append("<h2 class='report-title'>Scheduled Drug Report</h2>");
            html.append("<table><thead><tr>");
            List<String> headers = List.of(
                "Bill Date", "Bill No", "Patient No", "Patient", "Consultant",
                "Product Name", "Manufacturer", "Batch No", "Exp Date", "Qty", "Consultant Sign"
            );
            for (String h : headers) {
                html.append("<th>").append(h).append("</th>");
            }
            html.append("</tr></thead><tbody>");

            if (rows.isEmpty()) {
                String dateMsg = fromDateStr.equals(toDateStr) ? "on " + displayDate : "from " + displayDate;
                html.append("<tr><td colspan='11' class='error-msg-row'>No Record Found !!! There is no Scheduled drug report ").append(dateMsg).append("</td></tr>");
            } else {
                for (Map<String, Object> row : rows) {
                    html.append("<tr>");
                    
                    String billDate = reportEngine.formatGeneralValue(row.get("bill_date"));
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(billDate);
                        billDate = d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) {
                        // ignore
                    }
                    html.append("<td>").append(billDate).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("bill_no"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("patient_no"))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("patient_name")))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("consultant")))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("product_name")))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("manufacturer")))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("batch_number")))).append("</td>");
                    
                    String expDate = reportEngine.formatGeneralValue(row.get("expiry_date"));
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(expDate);
                        expDate = d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) {
                        // ignore
                    }
                    html.append("<td>").append(expDate).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("quantity"))).append("</td>");
                    html.append("<td></td>"); // Consultant Sign (blank space for physical signature)
                    html.append("</tr>");
                }
            }
            html.append("</tbody></table>");
            return html.toString();
        }

        if ("below_reorder_level".equals(reportName)) {
            String currentDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yy"));
            
            StringBuilder html = new StringBuilder();
            html.append("<style>")
                .append("body { font-family: 'Segoe UI', sans-serif; color: #1e293b; margin: 0; }")
                .append("table { border-collapse: collapse; width: 100%; font-size: 12px; margin-bottom: 25px; }")
                .append("thead tr { background: #1e40af; color: #fff; }")
                .append("th { padding: 8px 10px; text-align: left; white-space: nowrap; font-weight: 600; }")
                .append("td { padding: 6px 10px; border-bottom: 1px solid #e2e8f0; white-space: nowrap; text-align: left; }")
                .append("tr:nth-child(even) { background: #f8fafc; }")
                .append(".report-title { margin: 0; color: #1e40af; font-size: 20px; font-weight: 700; margin-bottom: 15px; }")
                .append(".error-msg-row { color: #ef4444; font-weight: bold; text-align: center; font-size: 14px; padding: 12px; font-style: italic; }")
                .append("</style>");

            html.append("<h2 class='report-title'>Reorder Report</h2>");
            html.append("<table><thead><tr>");
            List<String> headers = List.of("Product Name", "Current Stock", "Reorder Level", "Manufacturer");
            for (String h : headers) {
                html.append("<th>").append(h).append("</th>");
            }
            html.append("</tr></thead><tbody>");

            if (rows.isEmpty()) {
                html.append("<tr><td colspan='4' class='error-msg-row'>No Record Found !!! There is no Reorder on ").append(currentDate).append("</td></tr>");
            } else {
                for (Map<String, Object> row : rows) {
                    html.append("<tr>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("item_name")))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("current_stock"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("reorder_level"))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("manufacturer")))).append("</td>");
                    html.append("</tr>");
                }
            }
            html.append("</tbody></table>");
            return html.toString();
        }

        if ("stock_adjustments".equals(reportName)) {
            String fromDateStr = reportEngine.dateStr(params, "from_date");
            String toDateStr = reportEngine.dateStr(params, "to_date");
            String displayDate = "";
            try {
                java.time.LocalDate f = java.time.LocalDate.parse(fromDateStr);
                java.time.LocalDate t = java.time.LocalDate.parse(toDateStr);
                if (f.equals(t)) {
                    displayDate = f.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                } else {
                    displayDate = f.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")) + " to " + t.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }
            } catch (Exception e) {
                displayDate = fromDateStr + " to " + toDateStr;
            }

            StringBuilder html = new StringBuilder();
            html.append("<style>")
                .append("body { font-family: 'Segoe UI', sans-serif; color: #1e293b; margin: 0; }")
                .append("table { border-collapse: collapse; width: 100%; font-size: 12px; margin-bottom: 25px; }")
                .append("thead tr { background: #1e40af; color: #fff; }")
                .append("th { padding: 8px 10px; text-align: center; white-space: nowrap; font-weight: 600; }")
                .append("td { padding: 6px 10px; border-bottom: 1px solid #e2e8f0; white-space: nowrap; }")
                .append("tr:nth-child(even) { background: #f8fafc; }")
                .append(".report-title { margin: 0; color: #1e40af; font-size: 20px; font-weight: 700; margin-bottom: 15px; }")
                .append(".error-msg-row { color: #ef4444; font-weight: bold; text-align: center; font-size: 14px; padding: 12px; font-style: italic; }")
                .append("</style>");

            html.append("<h2 class='report-title'>Stock Correction Report</h2>");
            html.append("<table><thead><tr>");
            List<String> headers = List.of(
                "Correction No", "Correction Date", "Batch No", "Expiry Date",
                "Serial No", "Book Qty", "Purchase Rate", "Tax %", "Reason for Correction", "Authorised By"
            );
            for (String h : headers) {
                html.append("<th>").append(h).append("</th>");
            }
            html.append("</tr></thead><tbody>");

            if (rows.isEmpty()) {
                String dateMsg = fromDateStr.equals(toDateStr) ? "on " + displayDate : "from " + displayDate;
                html.append("<tr><td colspan='10' class='error-msg-row'>No Record Found !!! There is no Stock correction report ").append(dateMsg).append("</td></tr>");
            } else {
                String currentItem = null;
                for (Map<String, Object> row : rows) {
                    String itemName = reportEngine.formatGeneralValue(row.get("item_name"));
                    if (currentItem == null || !currentItem.equals(itemName)) {
                        currentItem = itemName;
                        html.append("<tr style='background: #f1f5f9; font-weight: bold; font-size: 13px;'>")
                            .append("<td colspan='10' style='text-align: left; padding: 8px 10px; border-bottom: 1px solid #cbd5e1; color: #334155;'>")
                            .append(reportEngine.escHtml(itemName))
                            .append("</td></tr>");
                    }
                    
                    html.append("<tr>");
                    html.append("<td style='text-align: center;'>").append(reportEngine.formatGeneralValue(row.get("stock_cor_no"))).append("</td>");
                    
                    String corDate = reportEngine.formatGeneralValue(row.get("stock_cor_date"));
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(corDate);
                        corDate = d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    } catch (Exception e) {
                        // ignore
                    }
                    html.append("<td style='text-align: center;'>").append(corDate).append("</td>");
                    html.append("<td style='text-align: center;'>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("batch_no")))).append("</td>");
                    
                    String expDate = reportEngine.formatGeneralValue(row.get("expiry_date"));
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(expDate);
                        expDate = d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    } catch (Exception e) {
                        // ignore
                    }
                    html.append("<td style='text-align: center;'>").append(expDate).append("</td>");
                    
                    html.append("<td style='text-align: center;'>-</td>"); // Serial No
                    html.append("<td style='text-align: right;'>").append(reportEngine.formatGeneralValue(row.get("quantity"))).append("</td>");
                    html.append("<td style='text-align: right;'>").append(reportEngine.formatGeneralValue(row.get("purchase_rate"))).append("</td>");
                    html.append("<td style='text-align: center;'>-</td>"); // Tax %
                    
                    String reason = reportEngine.formatGeneralValue(row.get("reason"));
                    if (reason == null || reason.trim().isEmpty() || "null".equalsIgnoreCase(reason)) {
                        reason = "-";
                    }
                    html.append("<td style='text-align: left;'>").append(reportEngine.escHtml(reason)).append("</td>");
                    html.append("<td style='text-align: center;'>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("authorised_by")))).append("</td>");
                    html.append("</tr>");
                }
            }
            html.append("</tbody></table>");
            return html.toString();
        }

        if ("slow_moving_items".equals(reportName)) {
            StringBuilder html = new StringBuilder();
            html.append("<style>")
                .append("body { font-family: 'Segoe UI', sans-serif; color: #1e293b; margin: 0; }")
                .append("table { border-collapse: collapse; width: 100%; font-size: 12px; margin-bottom: 25px; }")
                .append("thead tr { background: #1e40af; color: #fff; }")
                .append("th { padding: 8px 10px; text-align: left; white-space: nowrap; font-weight: 600; border: 1px solid #cbd5e1; }")
                .append("td { padding: 6px 10px; border: 1px solid #e2e8f0; white-space: nowrap; text-align: left; }")
                .append("tr:nth-child(even) { background: #f8fafc; }")
                .append(".report-title { margin: 0; color: #1e40af; font-size: 20px; font-weight: 700; margin-bottom: 15px; }")
                .append(".error-msg-row { color: #ef4444; font-weight: bold; text-align: center; font-size: 14px; padding: 12px; }")
                .append("</style>");

            html.append("<h2 class='report-title'>Non Moving Report</h2>");
            html.append("<table><thead><tr>");
            List<String> headers = List.of(
                "Product Name", "Batch No", "Expiry Date", "Qty", "Purchase",
                "CGST %", "CGST Amt", "SGST %", "SGST Amt", "IGST %", "IGST Amt", "Total Value", "MRP", "Invoice No", "Invoice Date"
            );
            for (String h : headers) {
                html.append("<th>").append(h).append("</th>");
            }
            html.append("</tr></thead><tbody>");

            if (rows.isEmpty()) {
                html.append("<tr><td colspan='15' class='error-msg-row'>No Record Found !!! There is no non moving stocks</td></tr>");
            } else {
                for (Map<String, Object> row : rows) {
                    html.append("<tr>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("Product Name")))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("Batch No")))).append("</td>");
                    
                    String expDate = reportEngine.formatGeneralValue(row.get("Expiry Date"));
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(expDate);
                        expDate = d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) {
                        // ignore
                    }
                    html.append("<td>").append(expDate).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("Qty"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("Purchase"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("CGST %"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("CGST Amt"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("SGST %"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("SGST Amt"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("IGST %"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("IGST Amt"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("Total Value"))).append("</td>");
                    html.append("<td>").append(reportEngine.formatGeneralValue(row.get("MRP"))).append("</td>");
                    html.append("<td>").append(reportEngine.escHtml(reportEngine.formatGeneralValue(row.get("Invoice No")))).append("</td>");
                    
                    String invDate = reportEngine.formatGeneralValue(row.get("Invoice Date"));
                    try {
                        java.time.LocalDate d = java.time.LocalDate.parse(invDate);
                        invDate = d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                    } catch (Exception e) {
                        // ignore
                    }
                    html.append("<td>").append(invDate).append("</td>");
                    html.append("</tr>");
                }
            }
            html.append("</tbody></table>");
            return html.toString();
        }
        return null;
    }
}
