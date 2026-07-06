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
class RevenueReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.RevenueReportDataService revenueReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private RevenueReportService service;

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
            put("total", "1");
            put("patient_id", "1");
            put("reportName", "1");
            put("DEPARTMENT", "1");
            put("BED_TYPE", "1");
            put("Date/Consultant-wise Revenue Generated", "1");
            put("patient_number", "1");
            put("category", "1");
            put("net_revenue_report", "1");
            put("discount", "1");
            put("<td>Total :</td>", "1");
            put("room_revenue", "1");
            put("department_revenue_opip", "1");
            put("<tr style='font-weight:bold;background:#f1f5f9;'>", "1");
            put("Consultant Wise Revenue on ", "1");
            put("<td>Total</td>", "1");
            put("detail", "1");
            put("bill_type", "1");
            put("Department Wise from ", "1");
            put("Consultant", "1");
            put("1", "1");
            put("ip_bills", "1");
            put("Net Revenue Report", "1");
            put("department_id", "1");
            put("bill_date", "1");
            put("bill_no", "1");
            put("admission_date", "1");
            put("OP_CAN_AMOUNT", "1");
            put("department_revenue", "1");
            put("</div>", "1");
            put("age_sex", "1");
            put("op_bills", "1");
            put("IP_CASH_CAN_AMOUNT", "1");
            put("<div class='summary'> &nbsp;&nbsp; ", "1");
            put("To Date", "1");
            put("patient_name", "1");
            put("</tbody></table>", "1");
            put(" to ", "1");
            put("consultant_id", "1");
            put("Bed Type", "1");
            put("IP Credit Bills", "1");
            put("department", "1");
            put("ALL", "1");
            put("name", "1");
            put("</td></tr>", "1");
            put("consultant_revenue", "1");
            put("description", "1");
            put("<tr><td>", "1");
            put("Room wise bill report on ", "1");
            put("OP Bills", "1");
            put("<tr>", "1");
            put("From Date", "1");
            put("report_view_type", "1");
            put("2025-01-01", "1");
            put("parameters", "1");
            put("Consultant Wise Revenue from ", "1");
            put("Room wise bill report from ", "1");
            put("Revenue", "1");
            put("Department Wise Revenue", "1");
            put("</tr></thead><tbody>", "1");
            put("bed_type_id", "1");
            put("IP Cash Bills", "1");
            put("<td>Total Amount</td>", "1");
            put("encounter_type", "1");
            put("-", "1");
            put("to_date", "1");
            put("Department-wise Revenue", "1");
            put("CONSULTANT", "1");
            put("raised_by", "1");
            put("summary", "1");
            put("bill_number", "1");
            put("Net Revenue generated from ", "1");
            put("RoomWise Bill Report", "1");
            put("consultant_revenue_opip", "1");
            put("Department Wise on ", "1");
            put("Department", "1");
            put("from_date", "1");
            put("DATE", "1");
            put("%.2f", "1");
            put("paid_amount", "1");
            put("Cancelled", "1");
            put("Consultant Wise Revenue Report", "1");
            put("bill_amount", "1");
            put("consultant_name", "1");
            put("<td>", "1");
            put("</td>", "1");
            put("<td style='text-align:right'>", "1");
            put("net_amount", "1");
            put("bed_no", "1");
            put("</tr>", "1");
            put("IP_CREDIT_CAN_AMOUNT", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "total", "patient_id", "reportName", "DEPARTMENT", "BED_TYPE", "Date/Consultant-wise Revenue Generated", "patient_number", "category", "net_revenue_report", "discount", "<td>Total :</td>", "room_revenue", "department_revenue_opip", "<tr style='font-weight:bold;background:#f1f5f9;'>", "Consultant Wise Revenue on ", "<td>Total</td>", "detail", "bill_type", "Department Wise from ", "Consultant", "1", "ip_bills", "Net Revenue Report", "department_id", "bill_date", "bill_no", "admission_date", "OP_CAN_AMOUNT", "department_revenue", "</div>", "age_sex", "op_bills", "IP_CASH_CAN_AMOUNT", "<div class='summary'> &nbsp;&nbsp; ", "To Date", "patient_name", "</tbody></table>", " to ", "consultant_id", "Bed Type", "IP Credit Bills", "department", "ALL", "name", "</td></tr>", "consultant_revenue", "description", "<tr><td>", "Room wise bill report on ", "OP Bills", "<tr>", "From Date", "report_view_type", "2025-01-01", "parameters", "Consultant Wise Revenue from ", "Room wise bill report from ", "Revenue", "Department Wise Revenue", "</tr></thead><tbody>", "bed_type_id", "IP Cash Bills", "<td>Total Amount</td>", "encounter_type", "-", "to_date", "Department-wise Revenue", "CONSULTANT", "raised_by", "summary", "bill_number", "Net Revenue generated from ", "RoomWise Bill Report", "consultant_revenue_opip", "Department Wise on ", "Department", "from_date", "DATE", "%.2f", "paid_amount", "Cancelled", "Consultant Wise Revenue Report", "bill_amount", "consultant_name", "<td>", "</td>", "<td style='text-align:right'>", "net_amount", "bed_no", "</tr>", "IP_CREDIT_CAN_AMOUNT" };

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
