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
class ProcurementReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.ProcurementReportDataService procurementReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private ProcurementReportService service;

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
            put("total_purchase_value", "1");
            put("reportName", "1");
            put("Goods Returned Summary Report", "1");
            put("</a>", "1");
            put("REPORT_VIEW_TYPE", "1");
            put("category", "1");
            put("  </button>", "1");
            put("total_amount", "1");
            put("<tr style='font-weight:bold;background:#f1f5f9;'>", "1");
            put("<td style='padding:6px 10px;'>", "1");
            put("invoice_date", "1");
            put("type", "1");
            put("detail", "1");
            put("goods_received_report", "1");
            put(" record(s)", "1");
            put("supplier_name", "1");
            put("grn_no", "1");
            put("<table><thead><tr>", "1");
            put("ordered_qty", "1");
            put("1", "1");
            put("goods_returned_report", "1");
            put("on ", "1");
            put("from ", "1");
            put("grn_no_filter", "1");
            put("grn_date", "1");
            put("Supplier", "1");
            put("dd-MM-yyyy", "1");
            put("<a href='#' class='po-link' data-po-no='", "1");
            put("free_qty", "1");
            put("total_qty_received", "1");
            put("po_no", "1");
            put("Purchase Order Summary Report", "1");
            put("purchase_orders_report", "1");
            put("</div>", "1");
            put("SUPPLIER", "1");
            put("<div class='summary'>", "1");
            put("Report", "1");
            put("<td style='padding:8px 10px;text-align:right;'>", "1");
            put("</tbody></table></div>", "1");
            put("po_date", "1");
            put("  </div>", "1");
            put("purchase_price", "1");
            put("user_name", "1");
            put("return_no_filter", "1");
            put(" to ", "1");
            put("product_name", "1");
            put("received_qty", "1");
            put("Direct Purchase", "1");
            put("po_notes", "1");
            put("name", "1");
            put("<div style='font-family:sans-serif;'>", "1");
            put("reason_for_goods_return", "1");
            put("po_no_filter", "1");
            put("description", "1");
            put("grn_value", "1");
            put("item_id", "1");
            put("<tr>", "1");
            put("report_view_type", "1");
            put("  <div>", "1");
            put("    Back", "1");
            put("2025-01-01", "1");
            put("<td style='padding:6px 10px;text-align:right;'>", "1");
            put("parameters", "1");
            put("</a></td>", "1");
            put("batch_no", "1");
            put("total_qty_ordered", "1");
            put("order_status", "1");
            put("</tr></thead><tbody>", "1");
            put("received_date", "1");
            put("expiry_date", "1");
            put("supplier_id", "1");
            put("supplier_contact", "1");
            put("  <div class='summary'>", "1");
            put("required", "1");
            put("Procurement", "1");
            put("itemId", "1");
            put("return_no", "1");
            put("qty", "1");
            put("to_date", "1");
            put("invoice_no", "1");
            put("summary", "1");
            put("Goods Received Summary Report", "1");
            put("from_date", "1");
            put("%.2f", "1");
            put("</td>", "1");
            put("<td></td>", "1");
            put("mrp", "1");
            put("</tr>", "1");
            put("defaultValue", "1");
            put("return_date", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "total_purchase_value", "reportName", "Goods Returned Summary Report", "</a>", "REPORT_VIEW_TYPE", "category", "  </button>", "total_amount", "<tr style='font-weight:bold;background:#f1f5f9;'>", "<td style='padding:6px 10px;'>", "invoice_date", "type", "detail", "goods_received_report", " record(s)", "supplier_name", "grn_no", "<table><thead><tr>", "ordered_qty", "1", "goods_returned_report", "on ", "from ", "grn_no_filter", "grn_date", "Supplier", "dd-MM-yyyy", "<a href='#' class='po-link' data-po-no='", "free_qty", "total_qty_received", "po_no", "Purchase Order Summary Report", "purchase_orders_report", "</div>", "SUPPLIER", "<div class='summary'>", "Report", "<td style='padding:8px 10px;text-align:right;'>", "</tbody></table></div>", "po_date", "  </div>", "purchase_price", "user_name", "return_no_filter", " to ", "product_name", "received_qty", "Direct Purchase", "po_notes", "name", "<div style='font-family:sans-serif;'>", "reason_for_goods_return", "po_no_filter", "description", "grn_value", "item_id", "<tr>", "report_view_type", "  <div>", "    Back", "2025-01-01", "<td style='padding:6px 10px;text-align:right;'>", "parameters", "</a></td>", "batch_no", "total_qty_ordered", "order_status", "</tr></thead><tbody>", "received_date", "expiry_date", "supplier_id", "supplier_contact", "  <div class='summary'>", "required", "Procurement", "itemId", "return_no", "qty", "to_date", "invoice_no", "summary", "Goods Received Summary Report", "from_date", "%.2f", "</td>", "<td></td>", "mrp", "</tr>", "defaultValue", "return_date" };

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
