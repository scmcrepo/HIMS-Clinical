package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DiagnosticsReportService extends BaseReportService {

    private final DiagnosticsReportDataService diagnosticsReportDataService;

    public DiagnosticsReportService(ReportEngine reportEngine, DiagnosticsReportDataService diagnosticsReportDataService) {
        super(reportEngine);
        this.diagnosticsReportDataService = diagnosticsReportDataService;
    }
    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "lab_tests_done", "description", "Date/Consultant-wise Lab Tests Done", "category", "Diagnostics"),
        Map.of("name", "lab_tests_done_detail", "description", "Test Done Summary Report", "category", "Diagnostics"),
        Map.of("name", "lab_pending", "description", "Current Pending Lab Tests", "category", "Diagnostics"),
        Map.of("name", "lab_pending_detail", "description", "Test Pending Detail Report", "category", "Diagnostics")
    );

    protected static final List<Map<String, Object>> DATE_AND_VISIT_PARAMS = List.of(
        param("from_date", "DATE", true,  "", "From date"),
        param("to_date",   "DATE", true,  "", "To date"),
        param("visit_type", "VISIT", false, "ALL", "Visit")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        m.put("lab_tests_done", DATE_RANGE_PARAMS);
        m.put("lab_tests_done_detail", DATE_RANGE_PARAMS);
        m.put("lab_pending", DATE_RANGE_PARAMS);
        m.put("lab_pending_detail", DATE_RANGE_PARAMS);
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
        String visitType = reportEngine.str(params, "visit_type");
        if (visitType == null || visitType.isBlank()) visitType = "ALL";

        return switch (reportName) {
            case "lab_tests_done" -> diagnosticsReportDataService.getLabTestsDoneSummary(from, to);
            case "lab_tests_done_detail" -> diagnosticsReportDataService.getLabTestsDoneDetail(from, to);
            case "lab_pending" -> diagnosticsReportDataService.getPendingLabTestsSummary(from, to);
            case "lab_pending_detail" -> diagnosticsReportDataService.getPendingLabTestsDetail(from, to);
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if ("lab_tests_done_detail".equals(reportName)) {
            return buildLabTestsDoneDetailHtml(rows, params);
        }
        if ("lab_pending_detail".equals(reportName)) {
            return buildLabPendingDetailHtml(rows, params);
        }
        return null;
    }

    private String buildLabTestsDoneDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='font-size:12px;color:#64748b;margin-bottom:12px;'>Number of Test Done from ").append(fmtDate(from)).append(" to ").append(fmtDate(to)).append("</div>");
        
        sb.append("<table><thead><tr>");
        String[] headers = {"Investigation Name", "No of test done for OP", "No of test done for IP", "Total test done"};
        for(String h: headers) sb.append("<th style='padding:8px 10px;text-align:left;'>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='4' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            String currentDept = null;
            long tOp = 0, tIp = 0, tTot = 0;
            
            for (Map<String, Object> r : rows) {
                String dept = reportEngine.str(r, "department");
                if (dept.isEmpty()) dept = "UNASSIGNED";
                
                if (!dept.equals(currentDept)) {
                    if (currentDept != null) {
                        // Print Total
                        sb.append("<tr style='font-weight:bold;border-bottom:1px dashed #cbd5e1;'>");
                        td(sb, "Total", "left");
                        td(sb, String.valueOf(tOp), "left");
                        td(sb, String.valueOf(tIp), "left");
                        td(sb, String.valueOf(tTot), "left");
                        sb.append("</tr>");
                    }
                    currentDept = dept;
                    tOp = 0; tIp = 0; tTot = 0;
                    sb.append("<tr style='font-weight:bold;'><td colspan='4' style='padding:12px 10px 4px 10px;'>Department : ").append(reportEngine.escHtml(dept)).append("</td></tr>");
                }
                
                long op = ((Number)r.getOrDefault("op_done", 0)).longValue();
                long ip = ((Number)r.getOrDefault("ip_done", 0)).longValue();
                long tot = ((Number)r.getOrDefault("total_done", 0)).longValue();
                tOp += op; tIp += ip; tTot += tot;
                
                sb.append("<tr>");
                td(sb, reportEngine.str(r, "investigation_name"), "left");
                td(sb, String.valueOf(op), "left");
                td(sb, String.valueOf(ip), "left");
                td(sb, String.valueOf(tot), "left");
                sb.append("</tr>");
            }
            if (currentDept != null) {
                sb.append("<tr style='font-weight:bold;border-bottom:1px dashed #cbd5e1;'>");
                td(sb, "Total", "left");
                td(sb, String.valueOf(tOp), "left");
                td(sb, String.valueOf(tIp), "left");
                td(sb, String.valueOf(tTot), "left");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildLabPendingDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='font-size:12px;color:#64748b;margin-bottom:12px;'>Number of Test Pending from ").append(fmtDate(from)).append(" to ").append(fmtDate(to)).append("</div>");
        
        sb.append("<table><thead><tr>");
        String[] headers = {"Order No","Bill No","Bill Date","Patient No","Patient","Consultant","Test Name","Specimen","Status"};
        for(String h: headers) sb.append("<th style='padding:8px 10px;text-align:left;'>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='9' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            String currentDept = null;
            for (Map<String, Object> r : rows) {
                String dept = reportEngine.str(r, "department");
                if (dept.isEmpty()) dept = "UNASSIGNED";
                
                if (!dept.equals(currentDept)) {
                    currentDept = dept;
                    sb.append("<tr style='font-weight:bold;'><td colspan='9' style='padding:12px 10px 4px 10px;'>Department : ").append(reportEngine.escHtml(dept)).append("</td></tr>");
                }
                
                sb.append("<tr>");
                td(sb, reportEngine.str(r, "order_no"), "left");
                td(sb, reportEngine.str(r, "bill_no"), "left");
                Object bDate = r.get("bill_date");
                td(sb, (bDate instanceof java.sql.Date || bDate instanceof java.time.LocalDate) ? reportEngine.formatDateValue(bDate) : reportEngine.formatGeneralValue(bDate), "left");
                td(sb, reportEngine.str(r, "patient_no"), "left");
                td(sb, reportEngine.str(r, "patient"), "left");
                td(sb, reportEngine.str(r, "consultant"), "left");
                td(sb, reportEngine.str(r, "test_name"), "left");
                td(sb, reportEngine.str(r, "specimen"), "left");
                td(sb, reportEngine.str(r, "status"), "left");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private void td(StringBuilder sb, String val, String align) {
        sb.append("<td style='padding:6px 10px;text-align:").append(align).append(";'>").append(reportEngine.escHtml(val)).append("</td>");
    }

    private String fmtDate(String iso) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(iso);
            return d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception e) { return iso; }
    }
}
