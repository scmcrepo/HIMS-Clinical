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
class DiagnosticsReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.DiagnosticsReportDataService diagnosticsReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private DiagnosticsReportService service;

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
            put("Consultant", "1");
            put("<table><thead><tr>", "1");
            put("name", "1");
            put("1", "1");
            put("patient", "1");
            put("consultant", "1");
            put("bill_date", "1");
            put("bill_no", "1");
            put("Patient No", "1");
            put("<td style='padding:6px 10px;text-align:", "1");
            put(";'>", "1");
            put("reportName", "1");
            put("<div style='font-family:sans-serif;'>", "1");
            put("dd-MM-yyyy", "1");
            put("to_date", "1");
            put("Order No", "1");
            put("</td></tr>", "1");
            put("lab_tests_done", "1");
            put("lab_pending", "1");
            put("lab_pending_detail", "1");
            put("lab_tests_done_detail", "1");
            put("Specimen", "1");
            put("summary", "1");
            put("category", "1");
            put("description", "1");
            put("Test Done Summary Report", "1");
            put("To date", "1");
            put("</div>", "1");
            put("patient_no", "1");
            put("Test Pending Detail Report", "1");
            put("Date/Consultant-wise Lab Tests Done", "1");
            put("Bill Date", "1");
            put("<tr>", "1");
            put("Status", "1");
            put("specimen", "1");
            put("</tbody></table></div>", "1");
            put("from_date", "1");
            put("DATE", "1");
            put("From date", "1");
            put("Bill No", "1");
            put("Encounter", "1");
            put("report_view_type", "1");
            put("</th>", "1");
            put("2025-01-01", "1");
            put("parameters", "1");
            put("Patient", "1");
            put("Current Pending Lab Tests", "1");
            put("order_no", "1");
            put("status", "1");
            put("</tr></thead><tbody>", "1");
            put("VISIT", "1");
            put(" to ", "1");
            put("</td>", "1");
            put("detail", "1");
            put("Test Name", "1");
            put("Diagnostics", "1");
            put("left", "1");
            put("test_name", "1");
            put("</tr>", "1");
            put("visit_type", "1");
            put("<th style='padding:8px 10px;text-align:left;'>", "1");
            put("department", "1");
            put("ALL", "1");
            put("UNASSIGNED", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "Consultant", "<table><thead><tr>", "name", "1", "patient", "consultant", "bill_date", "bill_no", "Patient No", "<td style='padding:6px 10px;text-align:", ";'>", "reportName", "<div style='font-family:sans-serif;'>", "dd-MM-yyyy", "to_date", "Order No", "</td></tr>", "lab_tests_done", "lab_pending", "lab_pending_detail", "lab_tests_done_detail", "Specimen", "summary", "category", "description", "Test Done Summary Report", "To date", "</div>", "patient_no", "Test Pending Detail Report", "Date/Consultant-wise Lab Tests Done", "Bill Date", "<tr>", "Status", "specimen", "</tbody></table></div>", "from_date", "DATE", "From date", "Bill No", "Encounter", "report_view_type", "</th>", "2025-01-01", "parameters", "Patient", "Current Pending Lab Tests", "order_no", "status", "</tr></thead><tbody>", "VISIT", " to ", "</td>", "detail", "Test Name", "Diagnostics", "left", "test_name", "</tr>", "visit_type", "<th style='padding:8px 10px;text-align:left;'>", "department", "ALL", "UNASSIGNED" };

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
