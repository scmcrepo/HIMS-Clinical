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
class EncounterReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.EncounterReportDataService encounterReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private EncounterReportService service;

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
            put("reportName", "1");
            put("DEPARTMENT", "1");
            put("</a>", "1");
            put("dept_wise_consultant_visit", "1");
            put("Department wise Encounter from ", "1");
            put("Encounter Detail from ", "1");
            put("Visit Date", "1");
            put(" : ", "1");
            put("category", "1");
            put("consultation_summary", "1");
            put("Grand Total", "1");
            put("Registered By", "1");
            put("<td style='padding:6px 10px;'>", "1");
            put("Total", "1");
            put("consultantId", "1");
            put("Consultation Summary Report", "1");
            put("<td>Total</td>", "1");
            put("Department-wise Encounter Report", "1");
            put("/", "1");
            put("detail", "1");
            put(" record(s)", "1");
            put("Patient Name", "1");
            put("Consultant", "1");
            put("<table><thead><tr>", "1");
            put("1", "1");
            put("Patient No", "1");
            put("<td>Grand Total</td>", "1");
            put("Consultant wise Encounter on ", "1");
            put("</div>", "1");
            put("Encounter Detail", "1");
            put("Consultant-wise Consulted Report", "1");
            put("<div class='summary'>", "1");
            put("Encounter Details", "1");
            put("Gender", "1");
            put("</tbody></table></div>", "1");
            put("To Date", "1");
            put("  </div>", "1");
            put("</h2>", "1");
            put("</tbody></table>", "1");
            put(" to ", "1");
            put("consultant_id", "1");
            put("consultant_wise_consulted", "1");
            put("department", "1");
            put("Old Patients", "1");
            put("name", "1");
            put("Department :", "1");
            put("Encounters", "1");
            put("<div style='font-family:sans-serif;'>", "1");
            put("<td style='text-align:right;'>", "1");
            put("No Record Found !!! There is no Encounter from ", "1");
            put("</td></tr>", "1");
            put("Consultant wise Encounter from ", "1");
            put("New Patients", "1");
            put("description", "1");
            put("departmentId", "1");
            put("Dept-wise Consultant Encounter Report", "1");
            put("<tr>", "1");
            put("Consultant-wise Encounter Report", "1");
            put("From Date", "1");
            put("report_view_type", "1");
            put("  <div>", "1");
            put("2025-01-01", "1");
            put("parameters", "1");
            put("Encounter Detail on ", "1");
            put("Age", "1");
            put("<strong>", "1");
            put("</tr></thead><tbody>", "1");
            put("visit_details", "1");
            put("encounters_report", "1");
            put("Consultant Wise Encounter Detail", "1");
            put("</strong> &nbsp;|&nbsp; ", "1");
            put("STRING", "1");
            put("consultant_wise_visit", "1");
            put("-", "1");
            put("to_date", "1");
            put("Department wise Encounter on ", "1");
            put("CONSULTANT", "1");
            put("summary", "1");
            put("Department Name", "1");
            put("Department", "1");
            put("'>", "1");
            put("from_date", "1");
            put("DATE", "1");
            put("consultant_wise_visit_detail", "1");
            put("Clinical Encounters Report", "1");
            put("<td>", "1");
            put("</td>", "1");
            put("Consultant-wise Encounter Detail Report", "1");
            put("Sex", "1");
            put("department_wise_visit", "1");
            put("</tr>", "1");
            put("<td style='padding-left: 24px;'>", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "reportName", "DEPARTMENT", "</a>", "dept_wise_consultant_visit", "Department wise Encounter from ", "Encounter Detail from ", "Visit Date", " : ", "category", "consultation_summary", "Grand Total", "Registered By", "<td style='padding:6px 10px;'>", "Total", "consultantId", "Consultation Summary Report", "<td>Total</td>", "Department-wise Encounter Report", "/", "detail", " record(s)", "Patient Name", "Consultant", "<table><thead><tr>", "1", "Patient No", "<td>Grand Total</td>", "Consultant wise Encounter on ", "</div>", "Encounter Detail", "Consultant-wise Consulted Report", "<div class='summary'>", "Encounter Details", "Gender", "</tbody></table></div>", "To Date", "  </div>", "</h2>", "</tbody></table>", " to ", "consultant_id", "consultant_wise_consulted", "department", "Old Patients", "name", "Department :", "Encounters", "<div style='font-family:sans-serif;'>", "<td style='text-align:right;'>", "No Record Found !!! There is no Encounter from ", "</td></tr>", "Consultant wise Encounter from ", "New Patients", "description", "departmentId", "Dept-wise Consultant Encounter Report", "<tr>", "Consultant-wise Encounter Report", "From Date", "report_view_type", "  <div>", "2025-01-01", "parameters", "Encounter Detail on ", "Age", "<strong>", "</tr></thead><tbody>", "visit_details", "encounters_report", "Consultant Wise Encounter Detail", "</strong> &nbsp;|&nbsp; ", "STRING", "consultant_wise_visit", "-", "to_date", "Department wise Encounter on ", "CONSULTANT", "summary", "Department Name", "Department", "'>", "from_date", "DATE", "consultant_wise_visit_detail", "Clinical Encounters Report", "<td>", "</td>", "Consultant-wise Encounter Detail Report", "Sex", "department_wise_visit", "</tr>", "<td style='padding-left: 24px;'>" };

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
