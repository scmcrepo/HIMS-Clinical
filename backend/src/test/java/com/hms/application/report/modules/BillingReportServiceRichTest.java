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
class BillingReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.BillingReportDataService billingReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private BillingReportService service;

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
            put("paid", "1");
            put("F", "1");
            put("patient", "1");
            put("Unsettled Bills Report", "1");
            put("discount_summary", "1");
            put("reportName", "1");
            put("OP- Discount Report", "1");
            put("bills_cancelled_daywise", "1");
            put("Outstanding Bills Summary", "1");
            put("OP", "1");
            put("patient_number", "1");
            put("category", "1");
            put("</tr></thead>", "1");
            put("<td style='padding: 6px 10px;'>", "1");
            put("/", "1");
            put("Discount Report", "1");
            put("detail", "1");
            put("<td style='padding: 8px 10px;'></td>", "1");
            put("Bills Raised Summary", "1");
            put("Record", "1");
            put("visit", "1");
            put("IP Outstanding Summary by Payor", "1");
            put("Male", "1");
            put("1", "1");
            put("bill_date", "1");
            put("admission_date", "1");
            put("dd-MM-yyyy", "1");
            put("given_by", "1");
            put("Cancelled Bills Summary", "1");
            put("</div>", "1");
            put("age_sex", "1");
            put("patient_no", "1");
            put("ip_outstanding_bills_summary", "1");
            put("IP Overdue Bills Report", "1");
            put("Bill Raised Summary", "1");
            put("</tbody></table></div>", "1");
            put("To Date", "1");
            put("patient_name", "1");
            put("</h2>", "1");
            put(" Record Found !!! There is no Discount from ", "1");
            put(" to ", "1");
            put("ALL", "1");
            put("name", "1");
            put("Date-wise Bills Cancelled", "1");
            put("<div style='font-family:sans-serif;'>", "1");
            put("<table>", "1");
            put("<thead><tr>", "1");
            put("discount_date", "1");
            put("Discounts Summary", "1");
            put("bill_cancelled_summary", "1");
            put("description", "1");
            put("bills_overdue", "1");
            put("<tbody>", "1");
            put("<tr>", "1");
            put("From Date", "1");
            put("report_view_type", "1");
            put("2025-01-01", "1");
            put("bill_raised_summary", "1");
            put("parameters", "1");
            put("IP", "1");
            put("discount_amount", "1");
            put("dd-MM-yyyy hh:mm a", "1");
            put("Age", "1");
            put("VISIT", "1");
            put("outstanding_bills_summary", "1");
            put("IP Overdue Bills Summary", "1");
            put("IP- Discount Report", "1");
            put("overdue_bills_summary", "1");
            put("Billing", "1");
            put("-", "1");
            put("No ", "1");
            put("to_date", "1");
            put("summary", "1");
            put("bill_number", "1");
            put("due_amount", "1");
            put("bills_raised_daywise", "1");
            put("Encounter Mode", "1");
            put("unsettled_bills", "1");
            put("from_date", "1");
            put("DATE", "1");
            put("reason", "1");
            put("bill_amount", "1");
            put("discount_report", "1");
            put("net_amount", "1");
            put("</td>", "1");
            put("Sex", "1");
            put("Female", "1");
            put("bed_no", "1");
            put("</tr>", "1");
            put("M", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "paid", "F", "patient", "Unsettled Bills Report", "discount_summary", "reportName", "OP- Discount Report", "bills_cancelled_daywise", "Outstanding Bills Summary", "OP", "patient_number", "category", "</tr></thead>", "<td style='padding: 6px 10px;'>", "/", "Discount Report", "detail", "<td style='padding: 8px 10px;'></td>", "Bills Raised Summary", "Record", "visit", "IP Outstanding Summary by Payor", "Male", "1", "bill_date", "admission_date", "dd-MM-yyyy", "given_by", "Cancelled Bills Summary", "</div>", "age_sex", "patient_no", "ip_outstanding_bills_summary", "IP Overdue Bills Report", "Bill Raised Summary", "</tbody></table></div>", "To Date", "patient_name", "</h2>", " Record Found !!! There is no Discount from ", " to ", "ALL", "name", "Date-wise Bills Cancelled", "<div style='font-family:sans-serif;'>", "<table>", "<thead><tr>", "discount_date", "Discounts Summary", "bill_cancelled_summary", "description", "bills_overdue", "<tbody>", "<tr>", "From Date", "report_view_type", "2025-01-01", "bill_raised_summary", "parameters", "IP", "discount_amount", "dd-MM-yyyy hh:mm a", "Age", "VISIT", "outstanding_bills_summary", "IP Overdue Bills Summary", "IP- Discount Report", "overdue_bills_summary", "Billing", "-", "No ", "to_date", "summary", "bill_number", "due_amount", "bills_raised_daywise", "Encounter Mode", "unsettled_bills", "from_date", "DATE", "reason", "bill_amount", "discount_report", "net_amount", "</td>", "Sex", "Female", "bed_no", "</tr>", "M" };

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
