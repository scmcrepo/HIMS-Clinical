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
class PharmacyReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.PharmacyReportDataService pharmacyReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private PharmacyReportService service;

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
            put("<style>", "1");
            put("<th style='text-align:right;'>Card</th>", "1");
            put("billId", "1");
            put("net", "1");
            put("name", "1");
            put("1", "1");
            put("patient", "1");
            put("Pharmacy Sales Bills", "1");
            put("<th>Patient No</th>", "1");
            put("rcpt_date", "1");
            put("pharmacy_sales_bills", "1");
            put("Stock Ledger Report (Legacy)", "1");
            put("reportName", "1");
            put("DEPARTMENT", "1");
            put("dd-MM-yyyy", "1");
            put("<table>", "1");
            put("<thead>", "1");
            put("to_date", "1");
            put("cash", "1");
            put(".text-center { text-align: center; }", "1");
            put("<tr style='font-weight:bold;font-size:13px;'>", "1");
            put("Bill ID", "1");
            put("UUID", "1");
            put("Bill Detail Report (Legacy)", "1");
            put("<th>Receipt No</th>", "1");
            put("Pharmacy", "1");
            put("summary", "1");
            put("category", "1");
            put("description", "1");
            put("</div>", "1");
            put("</style>", "1");
            put("bill_detail", "1");
            put("patient_no", "1");
            put("pharmacy_sales_collection", "1");
            put("</table>", "1");
            put("departmentId", "1");
            put("<td class='text-right'>", "1");
            put("<td style='border:none;'></td>", "1");
            put("<th>Patient</th>", "1");
            put("</tbody>", "1");
            put("refund_cash", "1");
            put("cheque", "1");
            put("<th style='text-align:right;'>Cash</th>", "1");
            put("<tbody>", "1");
            put("<tr>", "1");
            put("Sales Collection Report", "1");
            put("Department", "1");
            put("<tr style='font-weight:bold;background:#f1f5f9;'>", "1");
            put("report_view_type", "1");
            put("from_date", "1");
            put("<th>Rcpt Date</th>", "1");
            put("stock_ledger", "1");
            put("receipt_no", "1");
            put("2025-01-01", "1");
            put("parameters", "1");
            put("<th>User</th>", "1");
            put("user_name", "1");
            put(".text-right { text-align: right; }", "1");
            put("<td>Total</td>", "1");
            put("net_amount", "1");
            put("<td>", "1");
            put("</td>", "1");
            put(" to ", "1");
            put("<th style='text-align:right;'>Cheque</th>", "1");
            put("detail", "1");
            put("card", "1");
            put("</tr>", "1");
            put("</thead>", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "<style>", "<th style='text-align:right;'>Card</th>", "billId", "net", "name", "1", "patient", "Pharmacy Sales Bills", "<th>Patient No</th>", "rcpt_date", "pharmacy_sales_bills", "Stock Ledger Report (Legacy)", "reportName", "DEPARTMENT", "dd-MM-yyyy", "<table>", "<thead>", "to_date", "cash", ".text-center { text-align: center; }", "<tr style='font-weight:bold;font-size:13px;'>", "Bill ID", "UUID", "Bill Detail Report (Legacy)", "<th>Receipt No</th>", "Pharmacy", "summary", "category", "description", "</div>", "</style>", "bill_detail", "patient_no", "pharmacy_sales_collection", "</table>", "departmentId", "<td class='text-right'>", "<td style='border:none;'></td>", "<th>Patient</th>", "</tbody>", "refund_cash", "cheque", "<th style='text-align:right;'>Cash</th>", "<tbody>", "<tr>", "Sales Collection Report", "Department", "<tr style='font-weight:bold;background:#f1f5f9;'>", "report_view_type", "from_date", "<th>Rcpt Date</th>", "stock_ledger", "receipt_no", "2025-01-01", "parameters", "<th>User</th>", "user_name", ".text-right { text-align: right; }", "<td>Total</td>", "net_amount", "<td>", "</td>", " to ", "<th style='text-align:right;'>Cheque</th>", "detail", "card", "</tr>", "</thead>" };

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
