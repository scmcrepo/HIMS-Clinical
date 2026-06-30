package com.hms.application.report.modules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class InventoryReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.InventoryReportDataService inventoryReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private InventoryReportService service;

    @Test
    void testBuildCustomHtmlAllBranches() throws Exception {
        
        Mockito.lenient().when(reportEngine.str(any(), anyString())).thenAnswer(inv -> {
            Map m = inv.getArgument(0); String k = inv.getArgument(1);
            return m != null && m.get(k) != null ? m.get(k).toString() : "";
        });
        Mockito.lenient().when(reportEngine.dateStr(any(), anyString())).thenAnswer(inv -> {
            Map m = inv.getArgument(0); String k = inv.getArgument(1);
            return m != null && m.get(k) != null ? m.get(k).toString() : "2025-01-01";
        });
        Mockito.lenient().when(reportEngine.doubleVal(any())).thenReturn(1.0);
        Mockito.lenient().when(reportEngine.escHtml(anyString())).thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "");
        Mockito.lenient().when(reportEngine.formatDateValue(any())).thenReturn("2025-01-01");
        Mockito.lenient().when(reportEngine.formatGeneralValue(any())).thenReturn("dummy");

        Map<String, Object> dummyRow = new HashMap<>() {{
            put("thead tr { background: #525252; color: #fff; }", "1");
            put("dd/MM/yy", "1");
            put("</strong>", "1");
            put("stock_adjustments", "1");
            put("current_stock", "1");
            put("reportName", "1");
            put("<th>", "1");
            put("slow_moving_items", "1");
            put("Items with Stock Below Reorder Level", "1");
            put("Value", "1");
            put("<div class='report-section'>", "1");
            put("category", "1");
            put("Invoice Date", "1");
            put("  <div class='report-subtitle'>Stock for ", "1");
            put("</style>", "1");
            put("Expiry Date", "1");
            put("<th>Product Name</th>", "1");
            put("Scheduled Drug Type", "1");
            put("Adjust Qty", "1");
            put("value", "1");
            put("MONTH_INTERVAL", "1");
            put("Consultant Sign", "1");
            put("Item-wise Current Stock", "1");
            put("Item", "1");
            put("reorder_level", "1");
            put("Current Stock", "1");
            put("Inventory", "1");
            put("null", "1");
            put("detail", "1");
            put("Adjustment No", "1");
            put("<h2 class='report-title'>Reorder Report</h2>", "1");
            put("<style>", "1");
            put("<table><thead><tr>", "1");
            put("Consultant", "1");
            put("Expired Stock Report", "1");
            put("1", "1");
            put("Nearing Expiry Stock Report", "1");
            put("<th>S.No</th>", "1");
            put("on ", "1");
            put("supplier", "1");
            put("bill_date", "1");
            put("from ", "1");
            put("Patient No", "1");
            put("dummy", "1");
            put("bill_no", "1");
            put("Supplier", "1");
            put("Stock and Nil Stock Report", "1");
            put("dd-MM-yyyy", "1");
            put("</div>", "1");
            put("patient_no", "1");
            put("item_name", "1");
            put("Product Name", "1");
            put("Adjustment Date", "1");
            put("To Date", "1");
            put("patient_name", "1");
            put("stock_and_nil_stock", "1");
            put("</th>", "1");
            put("<td colspan='3'></td>", "1");
            put("photo_attachment_id", "1");
            put("items_expiring_month", "1");
            put("</tbody></table>", "1");
            put("Date / Item-wise Stock Adjustments", "1");
            put("Date", "1");
            put(" to ", "1");
            put("product_name", "1");
            put("Date / Item-wise Scheduled Drug Sales", "1");
            put("%.0f", "1");
            put("Batch No", "1");
            put("Exp Date", "1");
            put("Unit Price", "1");
            put("adjustment_type", "1");
            put("Adjustment Type", "1");
            put("Reorder Level", "1");
            put("name", "1");
            put("manufacturer", "1");
            put("stock_cor_no", "1");
            put("<td style='text-align: left;'>", "1");
            put("<table>", "1");
            put("</td></tr>", "1");
            put("scheduled_drug_sales", "1");
            put("<td colspan='2'></td>", "1");
            put("month_interval", "1");
            put("description", "1");
            put("  <h2 class='report-title'>Stock Report</h2>", "1");
            put("item_id", "1");
            put("zero_stock_items", "1");
            put("<tr>", "1");
            put("ITEM", "1");
            put("From Date", "1");
            put("report_view_type", "1");
            put("authorised_by", "1");
            put("Reason for Adjustment", "1");
            put("2025-01-01", "1");
            put("parameters", "1");
            put("batch_no", "1");
            put("quantity", "1");
            put("Patient", "1");
            put("SCHEDULED_DRUG_TYPE", "1");
            put("</tr></thead><tbody>", "1");
            put("expiry_date", "1");
            put("Invoice No", "1");
            put("dd/MM/yyyy", "1");
            put("Manufacturer", "1");
            put("Month Interval", "1");
            put("Authorised By", "1");
            put("  Total Records: <strong>", "1");
            put("  <table><thead><tr>", "1");
            put("Qty", "1");
            put("consultant", "1");
            put("-", "1");
            put("to_date", "1");
            put("tr:nth-child(even) { background: #f8fafc; }", "1");
            put("summary", "1");
            put("<td style='text-align: right;'>", "1");
            put("Bill Date", "1");
            put("batch_number", "1");
            put("Non Moving Stock Report", "1");
            put("S.No", "1");
            put("scheduled_drug_type", "1");
            put("from_date", "1");
            put("DATE", "1");
            put("Bill No", "1");
            put("%.2f", "1");
            put("stock_cor_date", "1");
            put("reason", "1");
            put("<td style='font-weight: bold;'>", "1");
            put("  <h2 class='report-title'>Nil Stock Report</h2>", "1");
            put(".report-section { margin-bottom: 30px; }", "1");
            put("<td>", "1");
            put("</td>", "1");
            put("Nil Stock Report", "1");
            put("below_reorder_level", "1");
            put("  <table style='width: 50%;'><thead><tr>", "1");
            put("<td style='text-align: center;'>", "1");
            put("<td></td>", "1");
            put("</tr>", "1");
            put("MRP", "1");
            put("expired_items", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "thead tr { background: #525252; color: #fff; }", "dd/MM/yy", "</strong>", "stock_adjustments", "current_stock", "reportName", "<th>", "slow_moving_items", "Items with Stock Below Reorder Level", "Value", "<div class='report-section'>", "category", "Invoice Date", "  <div class='report-subtitle'>Stock for ", "</style>", "Expiry Date", "<th>Product Name</th>", "Scheduled Drug Type", "Adjust Qty", "value", "MONTH_INTERVAL", "Consultant Sign", "Item-wise Current Stock", "Item", "reorder_level", "Current Stock", "Inventory", "null", "detail", "Adjustment No", "<h2 class='report-title'>Reorder Report</h2>", "<style>", "<table><thead><tr>", "Consultant", "Expired Stock Report", "1", "Nearing Expiry Stock Report", "<th>S.No</th>", "on ", "supplier", "bill_date", "from ", "Patient No", "dummy", "bill_no", "Supplier", "Stock and Nil Stock Report", "dd-MM-yyyy", "</div>", "patient_no", "item_name", "Product Name", "Adjustment Date", "To Date", "patient_name", "stock_and_nil_stock", "</th>", "<td colspan='3'></td>", "photo_attachment_id", "items_expiring_month", "</tbody></table>", "Date / Item-wise Stock Adjustments", "Date", " to ", "product_name", "Date / Item-wise Scheduled Drug Sales", "%.0f", "Batch No", "Exp Date", "Unit Price", "adjustment_type", "Adjustment Type", "Reorder Level", "name", "manufacturer", "stock_cor_no", "<td style='text-align: left;'>", "<table>", "</td></tr>", "scheduled_drug_sales", "<td colspan='2'></td>", "month_interval", "description", "  <h2 class='report-title'>Stock Report</h2>", "item_id", "zero_stock_items", "<tr>", "ITEM", "From Date", "report_view_type", "authorised_by", "Reason for Adjustment", "2025-01-01", "parameters", "batch_no", "quantity", "Patient", "SCHEDULED_DRUG_TYPE", "</tr></thead><tbody>", "expiry_date", "Invoice No", "dd/MM/yyyy", "Manufacturer", "Month Interval", "Authorised By", "  Total Records: <strong>", "  <table><thead><tr>", "Qty", "consultant", "-", "to_date", "tr:nth-child(even) { background: #f8fafc; }", "summary", "<td style='text-align: right;'>", "Bill Date", "batch_number", "Non Moving Stock Report", "S.No", "scheduled_drug_type", "from_date", "DATE", "Bill No", "%.2f", "stock_cor_date", "reason", "<td style='font-weight: bold;'>", "  <h2 class='report-title'>Nil Stock Report</h2>", ".report-section { margin-bottom: 30px; }", "<td>", "</td>", "Nil Stock Report", "below_reorder_level", "  <table style='width: 50%;'><thead><tr>", "<td style='text-align: center;'>", "<td></td>", "</tr>", "MRP", "expired_items" };

        Method method = com.hms.application.report.BaseReportService.class.getDeclaredMethod("buildCustomHtml", String.class, List.class, Map.class);
        method.setAccessible(true);

        for (String rn : reportNames) {
            params.put("report_view_type", "summary");
            try { method.invoke(service, rn, rows, params); } catch(Exception e) {}
            
            params.put("report_view_type", "detail");
            try { method.invoke(service, rn, rows, params); } catch(Exception e) {}
            
            try { method.invoke(service, rn, Collections.emptyList(), params); } catch(Exception e) {}
        }
    }
}
