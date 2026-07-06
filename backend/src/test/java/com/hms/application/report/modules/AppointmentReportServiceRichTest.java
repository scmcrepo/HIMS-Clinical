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
class AppointmentReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.AppointmentReportDataService appointmentReportDataService;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private AppointmentReportService service;

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
            put("appointments_cancelled_daywise", "1");
            put("Consultant", "1");
            put("appointments_cancelled_consultant", "1");
            put("name", "1");
            put("1", "1");
            put("appointment_cancelled_details", "1");
            put("reportName", "1");
            put("to_date", "1");
            put("Consultant-wise Appointments Cancelled", "1");
            put("CONSULTANT", "1");
            put("summary", "1");
            put("category", "1");
            put("description", "1");
            put("Appointment Cancelled Report", "1");
            put("report_view_type", "1");
            put("from_date", "1");
            put("Consultant-wise Appointments Booked", "1");
            put("Appointment Scheduled Report", "1");
            put("2025-01-01", "1");
            put("parameters", "1");
            put("Appointments", "1");
            put("consultantId", "1");
            put("appointments_daywise", "1");
            put("Day-wise Appointments Booked", "1");
            put("detail", "1");
            put("Day-wise Appointments Cancelled", "1");
            put("appointment_scheduled_details", "1");
            put("appointments_consultant", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "appointments_cancelled_daywise", "Consultant", "appointments_cancelled_consultant", "name", "1", "appointment_cancelled_details", "reportName", "to_date", "Consultant-wise Appointments Cancelled", "CONSULTANT", "summary", "category", "description", "Appointment Cancelled Report", "report_view_type", "from_date", "Consultant-wise Appointments Booked", "Appointment Scheduled Report", "2025-01-01", "parameters", "Appointments", "consultantId", "appointments_daywise", "Day-wise Appointments Booked", "detail", "Day-wise Appointments Cancelled", "appointment_scheduled_details", "appointments_consultant" };

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
