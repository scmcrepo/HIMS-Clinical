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

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if ("department_wise_visit".equals(reportName)) {
            return buildDepartmentWiseVisitHtml(rows, params);
        }
        if ("consultant_wise_visit".equals(reportName)) {
            return buildConsultantWiseVisitHtml(rows, params);
        }
        if ("consultant_wise_visit_detail".equals(reportName)) {
            return buildEncounterDetailHtml(rows, params, "Consultant Wise Encounter Detail");
        }
        if ("visit_details".equals(reportName)) {
            return buildEncounterDetailHtml(rows, params, "Encounter Detail");
        }
        return super.buildCustomHtml(reportName, rows, params);
    }

    private String buildDepartmentWiseVisitHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        String dateStr;
        if (from != null && from.equals(to)) {
            dateStr = "Department wise Encounter on " + formatDate(from);
        } else {
            dateStr = "Department wise Encounter from " + formatDate(from) + " to " + formatDate(to);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary'><strong>Department Wise Encounter Report</strong> &nbsp;|&nbsp; ")
          .append(dateStr)
          .append("</div>");

        sb.append("<table><thead><tr style='background-color: #525252; color: #ffffff;'>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Department</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>New Patients</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Old Patients</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Total</th>")
          .append("</tr></thead><tbody>");

        // Group the query results by Department name and collect departments with data
        List<String> deptsWithData = new ArrayList<>();
        Map<String, List<Map<String, Object>>> rowsByDept = new HashMap<>();
        Map<String, Object> grandTotalRow = null;

        for (Map<String, Object> row : rows) {
            String deptName = reportEngine.str(row, "Department").trim();
            if ("Grand Total".equalsIgnoreCase(deptName)) {
                grandTotalRow = row;
            } else {
                String key = deptName.toLowerCase();
                if (!rowsByDept.containsKey(key)) {
                    deptsWithData.add(deptName);
                }
                rowsByDept.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }

        long grandNew = 0;
        long grandOld = 0;
        long grandTotal = 0;

        if (grandTotalRow != null) {
            grandNew = reportEngine.toInt(grandTotalRow.get("New Patients"));
            grandOld = reportEngine.toInt(grandTotalRow.get("Old Patients"));
            grandTotal = reportEngine.toInt(grandTotalRow.get("Total"));
        } else {
            // fallback: manually compute from totals
            for (Map<String, Object> row : rows) {
                if ("Total".equals(reportEngine.str(row, "Consultant"))) {
                    grandNew += reportEngine.toInt(row.get("New Patients"));
                    grandOld += reportEngine.toInt(row.get("Old Patients"));
                    grandTotal += reportEngine.toInt(row.get("Total"));
                }
            }
        }

        if (deptsWithData.isEmpty()) {
            sb.append("<tr><td colspan='4' style='padding: 12px 10px; text-align: center; color: #ef4444; font-weight: 500; font-style: italic;'>")
              .append("No Record Found !!! There is no New Encounter from ").append(formatDate(from)).append(" to ").append(formatDate(to))
              .append("</td>")
              .append("</tr>");
        } else {
            for (String deptName : deptsWithData) {
                sb.append("<tr style='font-weight: bold; background: #f8fafc;'>")
                  .append("<td colspan='4' style='padding: 8px 10px; color: #1e293b;'>")
                  .append("Department :").append(reportEngine.escHtml(deptName.toUpperCase()))
                  .append("</td>")
                  .append("</tr>");

                List<Map<String, Object>> deptRows = rowsByDept.get(deptName.toLowerCase());
                Map<String, Object> deptTotalRow = null;
                List<Map<String, Object>> consultantRows = new ArrayList<>();
                for (Map<String, Object> r : deptRows) {
                    String cons = reportEngine.str(r, "Consultant");
                    if ("Total".equals(cons)) {
                        deptTotalRow = r;
                    } else {
                        consultantRows.add(r);
                    }
                }

                // Render consultant rows
                for (Map<String, Object> cr : consultantRows) {
                    String consultantName = reportEngine.str(cr, "Consultant");
                    String consultantId = reportEngine.str(cr, "consultant_id");
                    long newV = reportEngine.toInt(cr.get("New Patients"));
                    long oldV = reportEngine.toInt(cr.get("Old Patients"));
                    long tot = reportEngine.toInt(cr.get("Total"));

                    sb.append("<tr>")
                      .append("<td style='padding-left: 24px;'>");

                    if (!consultantId.isEmpty()) {
                        sb.append("<a href='#' class='report-drilldown' style='color: #2563eb; text-decoration: underline;' ")
                          .append("data-report='consultant_wise_visit_detail' data-consultant-id='").append(consultantId).append("'>")
                          .append(reportEngine.escHtml(consultantName))
                          .append("</a>");
                    } else {
                        sb.append(reportEngine.escHtml(consultantName));
                    }

                    sb.append("</td>")
                      .append("<td style='text-align:right;'>").append(newV).append("</td>")
                      .append("<td style='text-align:right;'>").append(oldV).append("</td>")
                      .append("<td style='text-align:right;'>").append(tot).append("</td>")
                      .append("</tr>");
                }

                // Render department total row
                long deptNew = 0;
                long deptOld = 0;
                long deptTot = 0;
                if (deptTotalRow != null) {
                    deptNew = reportEngine.toInt(deptTotalRow.get("New Patients"));
                    deptOld = reportEngine.toInt(deptTotalRow.get("Old Patients"));
                    deptTot = reportEngine.toInt(deptTotalRow.get("Total"));
                }
                sb.append("<tr style='font-weight: bold; border-top: 1px dashed #cbd5e1; border-bottom: 1px dashed #cbd5e1;'>")
                  .append("<td>Total</td>")
                  .append("<td style='text-align:right;'>").append(deptNew).append("</td>")
                  .append("<td style='text-align:right;'>").append(deptOld).append("</td>")
                  .append("<td style='text-align:right;'>").append(deptTot).append("</td>")
                  .append("</tr>");
            }
        }

        // Render Grand Total row
        sb.append("<tr style='font-weight: bold; background: #e2e8f0; font-size: 13px;'>")
          .append("<td>Grand Total</td>")
          .append("<td style='text-align:right;'>").append(grandNew).append("</td>")
          .append("<td style='text-align:right;'>").append(grandOld).append("</td>")
          .append("<td style='text-align:right;'>").append(grandTotal).append("</td>")
          .append("</tr>");

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String buildConsultantWiseVisitHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        String dateStr;
        if (from != null && from.equals(to)) {
            dateStr = "Consultant wise Encounter on " + formatDate(from);
        } else {
            dateStr = "Consultant wise Encounter from " + formatDate(from) + " to " + formatDate(to);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary'><strong>Consultant Wise Encounter Report</strong> &nbsp;|&nbsp; ")
          .append(dateStr)
          .append("</div>");

        sb.append("<table><thead><tr style='background-color: #525252; color: #ffffff;'>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Consultant</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>New Patients</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Old Patients</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Total</th>")
          .append("</tr></thead><tbody>");

        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='4' style='padding: 12px 10px; text-align: center; color: #94a3b8; font-style: italic;'>No Record Found !!!</td></tr>");
        } else {
            Map<String, Object> totalRow = null;
            List<Map<String, Object>> normalRows = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String consultant = reportEngine.str(row, "Consultant");
                if ("Total".equalsIgnoreCase(consultant)) {
                    totalRow = row;
                } else {
                    normalRows.add(row);
                }
            }

            for (Map<String, Object> r : normalRows) {
                String consultantName = reportEngine.str(r, "Consultant");
                String consultantId = reportEngine.str(r, "consultant_id");
                long newV = reportEngine.toInt(r.get("New Patients"));
                long oldV = reportEngine.toInt(r.get("Old Patients"));
                long tot = reportEngine.toInt(r.get("Total"));

                sb.append("<tr>")
                  .append("<td>");

                if (!consultantId.isEmpty()) {
                    sb.append("<a href='#' class='report-drilldown' style='color: #2563eb; text-decoration: underline;' ")
                      .append("data-report='consultant_wise_visit_detail' data-consultant-id='").append(consultantId).append("'>")
                      .append(reportEngine.escHtml(consultantName))
                      .append("</a>");
                } else {
                    sb.append(reportEngine.escHtml(consultantName));
                }

                sb.append("</td>")
                  .append("<td style='text-align:right;'>").append(newV).append("</td>")
                  .append("<td style='text-align:right;'>").append(oldV).append("</td>")
                  .append("<td style='text-align:right;'>").append(tot).append("</td>")
                  .append("</tr>");
            }

            // Render Total row
            long totalNew = 0;
            long totalOld = 0;
            long totalTot = 0;
            if (totalRow != null) {
                totalNew = reportEngine.toInt(totalRow.get("New Patients"));
                totalOld = reportEngine.toInt(totalRow.get("Old Patients"));
                totalTot = reportEngine.toInt(totalRow.get("Total"));
            } else {
                for (Map<String, Object> r : normalRows) {
                    totalNew += reportEngine.toInt(r.get("New Patients"));
                    totalOld += reportEngine.toInt(r.get("Old Patients"));
                    totalTot += reportEngine.toInt(r.get("Total"));
                }
            }

            sb.append("<tr style='font-weight: bold; background: #e2e8f0;'>")
              .append("<td>Total</td>")
              .append("<td style='text-align:right;'>").append(totalNew).append("</td>")
              .append("<td style='text-align:right;'>").append(totalOld).append("</td>")
              .append("<td style='text-align:right;'>").append(totalTot).append("</td>")
              .append("</tr>");
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String buildEncounterDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params, String reportTitle) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        String dateStr;
        if (from != null && from.equals(to)) {
            dateStr = "Encounter Detail on " + formatDate(from);
        } else {
            dateStr = "Encounter Detail from " + formatDate(from) + " to " + formatDate(to);
        }

        // Try to extract consultant name from the first row for the title
        String consultantName = "";
        if (!rows.isEmpty()) {
            consultantName = reportEngine.str(rows.get(0), "Consultant");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");

        // Header with title and Back button
        sb.append("<div style='display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; padding-bottom: 8px; border-bottom: 2px solid #e2e8f0;'>")
          .append("  <div>")
          .append("    <h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>").append(reportEngine.escHtml(reportTitle));
        if (!consultantName.isEmpty()) {
            sb.append(" : ").append(reportEngine.escHtml(consultantName));
        }
        sb.append("</h2>")
          .append("    <div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>").append(dateStr).append("</div>")
          .append("  </div>")
          .append("  <button class='encounter-back-btn' style='padding:6px 12px;background:#525252;color:#fff;border:none;border-radius:6px;cursor:pointer;font-weight:600;font-size:13px;display:inline-flex;align-items:center;gap:6px;box-shadow:0 1px 3px rgba(0,0,0,0.1);'>")
          .append("    <svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><line x1='19' y1='12' x2='5' y2='12'></line><polyline points='12 19 5 12 12 5'></polyline></svg>")
          .append("    Back")
          .append("  </button>")
          .append("</div>");

        // Summary bar
        sb.append("<div class='summary'>")
          .append("<strong>").append(reportEngine.escHtml(reportTitle)).append("</strong> &nbsp;|&nbsp; ")
          .append(rows.size()).append(" record(s)")
          .append("</div>");

        // Table header
        sb.append("<table><thead><tr>")
          .append("<th style='padding:8px 10px;text-align:left;'>Visit Date</th>")
          .append("<th style='padding:8px 10px;text-align:left;'>Patient No</th>")
          .append("<th style='padding:8px 10px;text-align:left;'>Patient Name</th>")
          .append("<th style='padding:8px 10px;text-align:left;'>Age/Sex</th>")
          .append("<th style='padding:8px 10px;text-align:left;'>Consultant</th>")
          .append("<th style='padding:8px 10px;text-align:left;'>Registered By</th>")
          .append("</tr></thead><tbody>");

        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='6' style='padding:12px 10px;text-align:center;color:#ef4444;font-weight:500;font-style:italic;'>")
              .append("No Record Found !!! There is no Encounter from ").append(formatDate(from)).append(" to ").append(formatDate(to))
              .append("</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                Object visitDateVal = r.get("Visit Date");
                String visitDate = visitDateVal != null ? reportEngine.formatDateValue(visitDateVal) : "";
                String patientNo = reportEngine.str(r, "Patient No");
                String patientName = reportEngine.str(r, "Patient Name");
                String age = reportEngine.str(r, "Age");
                // Gender can be in "Gender" or "Sex" column
                String sex = reportEngine.str(r, "Gender");
                if (sex.isEmpty()) sex = reportEngine.str(r, "Sex");
                String ageSex = age + "/" + sex;
                String consultant = reportEngine.str(r, "Consultant");
                String registeredBy = reportEngine.str(r, "Registered By");

                sb.append("<tr>")
                  .append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(visitDate)).append("</td>")
                  .append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(patientNo)).append("</td>")
                  .append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(patientName)).append("</td>")
                  .append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(ageSex)).append("</td>")
                  .append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(consultant)).append("</td>")
                  .append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(registeredBy)).append("</td>")
                  .append("</tr>");
            }
        }

        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "";
        try {
            String[] p = isoDate.split("-");
            return p[2] + "-" + p[1] + "-" + p[0];
        } catch (Exception e) { return isoDate; }
    }
}
