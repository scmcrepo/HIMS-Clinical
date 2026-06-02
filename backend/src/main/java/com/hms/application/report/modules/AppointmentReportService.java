package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AppointmentReportService extends BaseReportService {

    private final AppointmentReportDataService appointmentReportDataService;

    public AppointmentReportService(ReportEngine reportEngine, AppointmentReportDataService appointmentReportDataService) {
        super(reportEngine);
        this.appointmentReportDataService = appointmentReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "appointments_daywise", "description", "Day-wise Appointments Booked", "category", "Appointments"),
        Map.of("name", "appointment_scheduled_details", "description", "Appointment Scheduled Report", "category", "Appointments"),
        Map.of("name", "appointment_cancelled_details", "description", "Appointment Cancelled Report", "category", "Appointments"),
        Map.of("name", "appointments_consultant", "description", "Consultant-wise Appointments Booked", "category", "Appointments"),
        Map.of("name", "appointments_cancelled_daywise", "description", "Day-wise Appointments Cancelled", "category", "Appointments"),
        Map.of("name", "appointments_cancelled_consultant", "description", "Consultant-wise Appointments Cancelled", "category", "Appointments")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        
        List<Map<String, Object>> dateAndConsultantParams = new ArrayList<>(DATE_RANGE_PARAMS);
        dateAndConsultantParams.add(param("consultantId", "CONSULTANT", false, "", "Consultant"));
        
        for (Map<String, String> r : CATALOGUE) {
            String name = r.get("name");
            if (name.equals("appointments_daywise") || 
                name.equals("appointment_scheduled_details") || 
                name.equals("appointments_cancelled_daywise") || 
                name.equals("appointment_cancelled_details")) {
                m.put(name, dateAndConsultantParams);
            } else {
                m.put(name, DATE_RANGE_PARAMS);
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
        String consultantId = reportEngine.str(params, "consultantId");

        return switch (reportName) {
            case "appointments_daywise" -> appointmentReportDataService.getAppointmentsDaywise(from, to, consultantId);
            case "appointment_scheduled_details" -> appointmentReportDataService.getAppointmentScheduledDetails(from, to, consultantId);
            case "appointment_cancelled_details" -> appointmentReportDataService.getAppointmentCancelledDetails(from, to, consultantId);
            case "appointments_consultant" -> appointmentReportDataService.getAppointmentsConsultantwise(from, to);
            case "appointments_cancelled_daywise" -> appointmentReportDataService.getAppointmentsCancelledDaywise(from, to, consultantId);
            case "appointments_cancelled_consultant" -> appointmentReportDataService.getAppointmentsCancelledConsultantwise(from, to);
            default -> List.of();
        };
    }
}
