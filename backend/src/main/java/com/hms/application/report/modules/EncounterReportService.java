package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EncounterReportService extends BaseReportService {

    private final EncounterReportDataService encounterReportDataService;

    public EncounterReportService(ReportEngine reportEngine, EncounterReportDataService encounterReportDataService) {
        super(reportEngine);
        this.encounterReportDataService = encounterReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "encounters_report", "description", "Clinical Encounters Report", "category", "Encounters"),
        Map.of("name", "visit_details", "description", "Encounter Details", "category", "Encounters"),
        Map.of("name", "consultant_wise_visit", "description", "Consultant-wise Encounter Report", "category", "Encounters"),
        Map.of("name", "department_wise_visit", "description", "Department-wise Encounter Report", "category", "Encounters"),
        Map.of("name", "consultant_wise_consulted", "description", "Consultant-wise Consulted Report", "category", "Encounters"),
        Map.of("name", "consultation_summary", "description", "Consultation Summary Report", "category", "Encounters"),
        Map.of("name", "consultant_wise_visit_detail", "description", "Consultant-wise Encounter Detail Report", "category", "Encounters"),
        Map.of("name", "dept_wise_consultant_visit", "description", "Dept-wise Consultant Encounter Report", "category", "Encounters")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        List<Map<String, Object>> dateAndConsultant = List.of(
            param("from_date",    "DATE",       true,  "", "From Date"),
            param("to_date",      "DATE",       true,  "", "To Date"),
            param("consultant_id","CONSULTANT", false, "", "Consultant")
        );
        m.put("encounters_report", dateAndConsultant);
        m.put("visit_details", dateAndConsultant);
        m.put("consultant_wise_visit", DATE_RANGE_PARAMS);
        m.put("department_wise_visit", DATE_RANGE_PARAMS);
        m.put("consultation_summary", DATE_RANGE_PARAMS);

        m.put("consultant_wise_consulted", List.of(
            param("from_date",  "DATE",   true,  "", "From Date"),
            param("to_date",    "DATE",   true,  "", "To Date"),
            param("department", "STRING", true,  "", "Department Name")
        ));
        m.put("consultant_wise_visit_detail", List.of(
            param("from_date",    "DATE",       true,  "", "From Date"),
            param("to_date",      "DATE",       true,  "", "To Date"),
            param("consultantId", "CONSULTANT", true,  "", "Consultant")
        ));
        m.put("dept_wise_consultant_visit", List.of(
            param("from_date",    "DATE",       true,  "", "From Date"),
            param("to_date",      "DATE",       true,  "", "To Date"),
            param("departmentId", "DEPARTMENT", true,  "", "Department")
        ));
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
        String consultantId = reportEngine.str(params, "consultant_id");

        return switch (reportName) {
            case "encounters_report" -> encounterReportDataService.getEncountersReport(from, to, consultantId);
            case "visit_details" -> encounterReportDataService.getVisitDetails(from, to, consultantId);
            case "consultant_wise_visit" -> encounterReportDataService.getConsultantWiseVisitReport(from, to);
            case "department_wise_visit" -> encounterReportDataService.getDepartmentWiseVisitReport(from, to);
            case "consultation_summary" -> encounterReportDataService.getConsultationSummaryReport(from, to);
            case "consultant_wise_consulted" -> encounterReportDataService.getConsultantWiseConsultedReport(from, to, reportEngine.str(params, "department"));
            case "consultant_wise_visit_detail" -> encounterReportDataService.getConsultantWiseVisitDetail(from, to, reportEngine.str(params, "consultantId"));
            case "dept_wise_consultant_visit" -> encounterReportDataService.getDeptWiseConsultantVisit(from, to, reportEngine.str(params, "departmentId"));
            default -> List.of();
        };
    }
}
