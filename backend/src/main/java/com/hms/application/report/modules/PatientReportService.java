package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PatientReportService extends BaseReportService {

    private final PatientReportDataService patientReportDataService;

    public PatientReportService(ReportEngine reportEngine, PatientReportDataService patientReportDataService) {
        super(reportEngine);
        this.patientReportDataService = patientReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "patient_registration_daywise", "description", "Day-wise Patient Registrations", "category", "Patient"),
        Map.of("name", "patient_registration_details", "description", "Registration Report", "category", "Patient"),
        Map.of("name", "patient_registration", "description", "Patient Registration Report (Legacy)", "category", "Patient")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        
        List<Map<String, Object>> dateAndConsultantParams = new ArrayList<>(DATE_RANGE_PARAMS);
        dateAndConsultantParams.add(param("consultantId", "CONSULTANT", false, "", "Consultant"));
        
        m.put("patient_registration_daywise", dateAndConsultantParams);
        m.put("patient_registration_details", dateAndConsultantParams);
        m.put("patient_registration", dateAndConsultantParams);

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
            case "patient_registration_daywise", "patient_registration"
                -> patientReportDataService.getPatientRegistrationDaywise(from, to, consultantId);
            case "patient_registration_details"
                -> patientReportDataService.getPatientRegistrationDetails(from, to, consultantId);
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if (rows.isEmpty()) return null;

        return null;
    }
}
