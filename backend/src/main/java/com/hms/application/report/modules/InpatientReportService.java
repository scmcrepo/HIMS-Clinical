package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class InpatientReportService extends BaseReportService {

    private final InpatientReportDataService inpatientReportDataService;

    public InpatientReportService(ReportEngine reportEngine, InpatientReportDataService inpatientReportDataService) {
        super(reportEngine);
        this.inpatientReportDataService = inpatientReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "admissions_report", "description", "Date / Consultant-wise Admissions", "category", "Inpatient"),
        Map.of("name", "discharges_report", "description", "Date-wise Discharges", "category", "Inpatient"),
        Map.of("name", "bed_occupancy_period", "description", "Month / Year-wise Bed Occupancy", "category", "Inpatient"),
        Map.of("name", "beds_transferred", "description", "Date / Period-wise Beds Transferred", "category", "Inpatient"),
        Map.of("name", "bed_occupancy", "description", "Current Bed Occupancy status", "category", "Inpatient"),
        Map.of("name", "ip_discharge_summary", "description", "IP Discharge Summary (Legacy)", "category", "Inpatient")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        m.put("admissions_report", List.of(
            param("from_date", "DATE", true,  "", "From date"),
            param("to_date",   "DATE", true,  "", "To date"),
            param("report_type", "REPORT_TYPE", false, "SUMMARY", "Report")
        ));
        m.put("discharges_report", DATE_RANGE_PARAMS);
        m.put("bed_occupancy_period", List.of(
            param("year", "YEAR", true, "2026", "Year")
        ));
        m.put("beds_transferred", DATE_RANGE_PARAMS);
        m.put("bed_occupancy", List.of());
        m.put("ip_discharge_summary", List.of(
            param("encounterId", "UUID", true, "", "Encounter ID")
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

        return switch (reportName) {
            case "admissions_report" -> {
                String reportType = reportEngine.str(params, "report_type");
                if ("SUMMARY".equalsIgnoreCase(reportType) || reportType.isEmpty()) {
                    yield inpatientReportDataService.getAdmissionsSummaryReport(from, to);
                } else {
                    yield inpatientReportDataService.getAdmissionsReport(from, to);
                }
            }
            case "discharges_report" -> inpatientReportDataService.getDischargesReport(from, to);
            case "bed_occupancy_period" -> {
                String yr = reportEngine.str(params, "year");
                if (yr == null || yr.isEmpty()) {
                    yr = "2026";
                }
                yield inpatientReportDataService.getBedOccupancyPeriodReport(yr + "-01-01", yr + "-12-31");
            }
            case "beds_transferred" -> inpatientReportDataService.getBedsTransferredReport(from, to);
            case "bed_occupancy" -> inpatientReportDataService.getBedOccupancy();
            case "ip_discharge_summary" -> List.of(inpatientReportDataService.getDischargeSummary(reportEngine.uuid(params, "encounterId")));
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if ("admissions_report".equals(reportName)) {
            String reportType = reportEngine.str(params, "report_type");
            if ("SUMMARY".equalsIgnoreCase(reportType) || reportType.isEmpty()) {
                return buildAdmissionsSummaryHtml(rows, params);
            } else {
                return buildAdmissionsDetailHtml(rows, params);
            }
        } else if ("discharges_report".equals(reportName)) {
            return buildDischargesHtml(rows, params);
        } else if ("bed_occupancy_period".equals(reportName)) {
            return buildBedOccupancyHtml(rows, params);
        } else if ("beds_transferred".equals(reportName)) {
            return buildBedsTransferredHtml(rows, params);
        }
        return null;
    }

    private String buildBedsTransferredHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }

        String periodStr = fromDate.equals(toDate) 
            ? fromDate 
            : fromDate + " to " + toDate;

        sb.append("<div style='font-family: Arial, sans-serif; padding: 20px;'>");
        sb.append("  <div style='text-align: left; margin-bottom: 20px;'>");
        sb.append("    <h2 style='font-size: 18px; font-weight: bold; margin: 0; color: #111;'>Bed Transfer Report</h2>");
        sb.append("  </div>");

        String[] headers = {
            "Transfer Date", "Patient No", "Patient", "Age/Sex", 
            "Bed Transfer From", "Bed Type Transfer From", "Bed Transfer To", "Bed Type Transfer To", "Registered By"
        };
        String[] keys = {
            "Transfer Date", "Patient No", "Patient Name", "Age/Sex", 
            "Bed Transfer From", "Bed Type Transfer From", "Bed Transfer To", "Bed Type Transfer To", "Registered By"
        };

        sb.append("<table>");
        sb.append("<thead><tr style='background: #1e40af; color: #fff;'>");
        for (String h : headers) {
            sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left; white-space: nowrap; border-right: 1px solid rgba(255,255,255,0.15);'>").append(h).append("</th>");
        }
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        if (rows.isEmpty() || (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__")))) {
            sb.append("      <tr>");
            sb.append("        <td colspan='").append(headers.length).append("' style='text-align: center; color: #ef4444; font-style: italic; font-weight: bold; padding: 15px; border: none; font-size: 13px;'>");
            sb.append("          No Record Found !!! There is no bed transfer on ").append(periodStr);
            sb.append("        </td>");
            sb.append("      </tr>");
        } else {
            for (Map<String, Object> r : rows) {
                sb.append("      <tr>");
                for (String key : keys) {
                    Object v;
                    if ("Age/Sex".equals(key)) {
                        String ageVal = r.get("Age") != null ? r.get("Age").toString() : "";
                        ageVal = ageVal.replaceAll("\\s*Y$", "").trim();
                        String displayAge = ageVal.isEmpty() ? "-" : ageVal;
                        String sexVal = r.get("Gender") != null ? r.get("Gender").toString().toUpperCase() : "";
                        String sex = sexVal.isEmpty() ? "-" :
                            sexVal.startsWith("M") ? "M" :
                            sexVal.startsWith("F") ? "F" : "-";
                        v = displayAge + "/" + sex;
                    } else {
                        v = r.get(key);
                    }
                    String val = v != null ? reportEngine.escHtml(v.toString()) : "";
                    sb.append("        <td style='padding: 6px 10px; white-space: nowrap;'>").append(val).append("</td>");
                }
                sb.append("      </tr>");
            }
        }
        sb.append("    </tbody>");
        sb.append("  </table>");

        // Footer table info (matching generated by, generated date, and page info)
        String generatedDate = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm a").format(new Date());
        sb.append("<table style='width: 100%; font-size: 11px; color: #666; margin-top: 30px; border-top: 1px solid #eee; padding-top: 10px;'>");
        sb.append("  <tr>");
        sb.append("    <td style='text-align: left; width: 33%;'>Generated By : App Admin</td>");
        sb.append("    <td style='text-align: center; width: 33%;'>").append(generatedDate).append("</td>");
        sb.append("    <td style='text-align: right; width: 33%;'>Page 1 of 1</td>");
        sb.append("  </tr>");
        sb.append("</table>");

        sb.append("</div>");
        return sb.toString();
    }

    private String buildAdmissionsSummaryHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }

        String periodStr = fromDate.equals(toDate) 
            ? "on " + toDate 
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='text-align: left; margin-bottom: 20px;'>");
        sb.append("<h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Admission Summary Report</h2>");
        sb.append("<div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Summary report ").append(periodStr).append("</div>");
        sb.append("</div>");

        sb.append("<table>");
        sb.append("<thead><tr style='background: #1e40af; color: #fff;'>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left; border-right: 1px solid #ffffff;'>Department</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: center; width: 120px; border-right: 1px solid #ffffff;'>Male</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: center; width: 120px; border-right: 1px solid #ffffff;'>Female</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: center; width: 120px;'>Total</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        if (rows.isEmpty() || (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__")))) {
            sb.append("<tr><td colspan='4' style='padding: 20px; text-align: center; color: #94a3b8; font-style: italic;'>No records to display</td></tr>");
        } else {
            int grandMale = 0;
            int grandFemale = 0;
            int grandTotal = 0;

            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "department"))).append("</td>");
                
                int male = (int) reportEngine.doubleVal(r.get("male"));
                int female = (int) reportEngine.doubleVal(r.get("female"));
                int total = (int) reportEngine.doubleVal(r.get("total"));
                
                grandMale += male;
                grandFemale += female;
                grandTotal += total;

                sb.append("<td style='padding: 6px 10px; text-align: center;'>").append(male).append("</td>");
                sb.append("<td style='padding: 6px 10px; text-align: center;'>").append(female).append("</td>");
                sb.append("<td style='padding: 6px 10px; text-align: center;'>").append(total).append("</td>");
                sb.append("</tr>");
            }

            // Optional Grand Total Row
            sb.append("<tr style='background: #f8fafc; font-weight: bold; border-top: 2px solid #cbd5e1;'>");
            sb.append("<td style='padding: 8px 10px; text-align: right;'>Total</td>");
            sb.append("<td style='padding: 8px 10px; text-align: center;'>").append(grandMale).append("</td>");
            sb.append("<td style='padding: 8px 10px; text-align: center;'>").append(grandFemale).append("</td>");
            sb.append("<td style='padding: 8px 10px; text-align: center;'>").append(grandTotal).append("</td>");
            sb.append("</tr>");
        }

        sb.append("</tbody></table>");

        // Footer table info (matching generated by, generated date, and page info)
        String generatedDate = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm a").format(new Date());
        sb.append("<table style='width: 100%; font-size: 11px; color: #666; margin-top: 30px; border-top: 1px solid #eee; padding-top: 10px;'>");
        sb.append("  <tr>");
        sb.append("    <td style='text-align: left; width: 33%;'>Generated By : App Admin</td>");
        sb.append("    <td style='text-align: center; width: 33%;'>").append(generatedDate).append("</td>");
        sb.append("    <td style='text-align: right; width: 33%;'>Page 1 of 1</td>");
        sb.append("  </tr>");
        sb.append("</table>");

        sb.append("</div>");
        return sb.toString();
    }

    private String buildAdmissionsDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }

        String periodStr = fromDate.equals(toDate)
            ? "on " + toDate
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='text-align: left; margin-bottom: 20px;'>");
        sb.append("<h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Admission Detailed Report</h2>");
        sb.append("<div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Admission ").append(periodStr).append("</div>");
        sb.append("</div>");

        // Column definitions
        String[] headers = {"Patient No", "Admission Date", "Patient", "Age/Sex", "Consultant", "Department", "Bed No", "Ward", "Registered By"};
        String[] keys    = {"Patient No", "Admission Date", "Patient Name", "Age/Sex", "Consultant", "Department", "Bed No", "Ward", "Registered By"};

        sb.append("<table>");
        sb.append("<thead><tr style='background: #1e40af; color: #fff;'>");
        for (String h : headers) {
            sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left; white-space: nowrap; border-right: 1px solid rgba(255,255,255,0.15);'>").append(h).append("</th>");
        }
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        if (rows.isEmpty() || (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__")))) {
            sb.append("<tr><td colspan='").append(headers.length).append("' style='padding: 20px; text-align: center; color: #94a3b8; font-style: italic;'>No records to display</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                for (String key : keys) {
                    Object v;
                    if ("Age/Sex".equals(key)) {
                        String ageVal = r.get("Age") != null ? r.get("Age").toString() : "";
                        ageVal = ageVal.replaceAll("\\s*Y$", "").trim();
                        String displayAge = ageVal.isEmpty() ? "-" : ageVal;
                        String sexVal = r.get("Gender") != null ? r.get("Gender").toString().toUpperCase() : "";
                        String sex = sexVal.isEmpty() ? "-" :
                            sexVal.startsWith("M") ? "M" :
                            sexVal.startsWith("F") ? "F" : "-";
                        v = displayAge + "/" + sex;
                    } else {
                        v = r.get(key);
                    }
                    String val = v != null ? reportEngine.escHtml(v.toString()) : "";
                    sb.append("<td style='padding: 6px 10px; white-space: nowrap;'>").append(val).append("</td>");
                }
                sb.append("</tr>");
            }
        }

        sb.append("</tbody></table>");

        // Footer table info (matching generated by, generated date, and page info)
        String generatedDate = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm a").format(new Date());
        sb.append("<table style='width: 100%; font-size: 11px; color: #666; margin-top: 30px; border-top: 1px solid #eee; padding-top: 10px;'>");
        sb.append("  <tr>");
        sb.append("    <td style='text-align: left; width: 33%;'>Generated By : App Admin</td>");
        sb.append("    <td style='text-align: center; width: 33%;'>").append(generatedDate).append("</td>");
        sb.append("    <td style='text-align: right; width: 33%;'>Page 1 of 1</td>");
        sb.append("  </tr>");
        sb.append("</table>");

        sb.append("</div>");
        return sb.toString();
    }

    private String buildDischargesHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        String fromDate = reportEngine.dateStr(params, "from_date");
        String toDate = reportEngine.dateStr(params, "to_date");
        try {
            java.time.LocalDate fd = java.time.LocalDate.parse(fromDate);
            java.time.LocalDate td = java.time.LocalDate.parse(toDate);
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            fromDate = fd.format(dtf);
            toDate = td.format(dtf);
        } catch (Exception e) {
            // keep as is
        }

        String periodStr = fromDate.equals(toDate)
            ? "on " + toDate
            : "from " + fromDate + " to " + toDate;

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='text-align: left; margin-bottom: 20px;'>");
        sb.append("<h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>Discharge Report</h2>");
        sb.append("<div style='font-size: 13px; color: #64748b; font-weight: bold; margin-top: 4px;'>Discharge ").append(periodStr).append("</div>");
        sb.append("</div>");

        // Column definitions
        String[] headers = {"Reg Date", "Patient No", "Patient", "Age/Sex", "Admission Date", "Discharge Date", "Bed No", "Consultant", "Registered By"};
        String[] keys    = {"Reg Date", "Patient No", "Patient Name", "Age/Sex", "Admission Date", "Discharge Date", "Bed No", "Consultant", "Registered By"};

        sb.append("<table>");
        sb.append("<thead><tr style='background: #1e40af; color: #fff;'>");
        for (String h : headers) {
            sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left; white-space: nowrap; border-right: 1px solid rgba(255,255,255,0.15);'>").append(h).append("</th>");
        }
        sb.append("</tr></thead>");
        sb.append("<tbody>");

        if (rows.isEmpty() || (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__")))) {
            sb.append("<tr><td colspan='").append(headers.length).append("' style='padding: 20px; text-align: center; color: #94a3b8; font-style: italic;'>No records to display</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                for (String key : keys) {
                    Object v;
                    if ("Age/Sex".equals(key)) {
                        String ageVal = r.get("Age") != null ? r.get("Age").toString() : "";
                        ageVal = ageVal.replaceAll("\\s*Y$", "").trim();
                        String displayAge = ageVal.isEmpty() ? "-" : ageVal;
                        String sexVal = r.get("Gender") != null ? r.get("Gender").toString().toUpperCase() : "";
                        String sex = sexVal.isEmpty() ? "-" :
                            sexVal.startsWith("M") ? "M" :
                            sexVal.startsWith("F") ? "F" : "-";
                        v = displayAge + "/" + sex;
                    } else {
                        v = r.get(key);
                    }
                    String val = v != null ? reportEngine.escHtml(v.toString()) : "";
                    sb.append("<td style='padding: 6px 10px; white-space: nowrap;'>").append(val).append("</td>");
                }
                sb.append("</tr>");
            }
        }

        sb.append("</tbody></table>");

        // Footer table info (matching generated by, generated date, and page info)
        String generatedDate = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm a").format(new Date());
        sb.append("<table style='width: 100%; font-size: 11px; color: #666; margin-top: 30px; border-top: 1px solid #eee; padding-top: 10px;'>");
        sb.append("  <tr>");
        sb.append("    <td style='text-align: left; width: 33%;'>Generated By : App Admin</td>");
        sb.append("    <td style='text-align: center; width: 33%;'>").append(generatedDate).append("</td>");
        sb.append("    <td style='text-align: right; width: 33%;'>Page 1 of 1</td>");
        sb.append("  </tr>");
        sb.append("</table>");

        sb.append("</div>");
        return sb.toString();
    }

    private String buildBedOccupancyHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String year = reportEngine.str(params, "year");
        if (year == null || year.isEmpty()) {
            year = "2026";
        }

        java.time.LocalDate now = java.time.LocalDate.now();
        int maxMonth = 12;
        try {
            int selYear = Integer.parseInt(year);
            if (selYear == now.getYear()) {
                maxMonth = now.getMonthValue() - 1;
            } else if (selYear > now.getYear()) {
                maxMonth = 0;
            }
        } catch (Exception e) {
            // fallback to 12
        }

        // Filter rows to only include months up to maxMonth
        List<Map<String, Object>> filteredRows = new ArrayList<>();
        String currentPeriodLimit = String.format("%s-%02d", year, maxMonth);
        for (Map<String, Object> row : rows) {
            String period = (String) row.get("period");
            if (period != null) {
                if (period.compareTo(currentPeriodLimit) <= 0) {
                    filteredRows.add(row);
                }
            } else {
                filteredRows.add(row);
            }
        }
        rows = filteredRows;

        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family: Arial, sans-serif; padding: 20px; color: #333; max-width: 800px; margin: 0 auto;'>");
        
        // Report Header
        html.append("<div style='text-align: center; margin-bottom: 25px;'>");
        html.append("  <h2 style='margin: 0; font-size: 18px; font-weight: bold; color: #111;'>Bed Occupancy Report</h2>");
        html.append("</div>");

        // Group rows by period month (e.g. "2026-01", "2026-02", ...)
        Map<String, List<Map<String, Object>>> monthRows = new LinkedHashMap<>();
        for (int m = 1; m <= maxMonth; m++) {
            String monthKey = String.format("%s-%02d", year, m);
            monthRows.put(monthKey, new ArrayList<>());
        }

        // Group rows by ward for the summary table
        Map<String, List<Map<String, Object>>> wardRows = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            String period = (String) row.get("period");
            if (period != null && monthRows.containsKey(period)) {
                monthRows.get(period).add(row);
            }
            
            String ward = (String) row.get("ward");
            if (ward != null) {
                wardRows.computeIfAbsent(ward, k -> new ArrayList<>()).add(row);
            }
        }

        // Bed Occupancy Summary Table
        html.append("<div style='margin-bottom: 30px;'>");
        html.append("  <h3 style='margin: 0 0 10px 0; font-size: 14px; font-weight: bold; color: #111;'>Bed Occupancy Summary</h3>");
        html.append("  <table style='width: 100%; border-collapse: collapse; font-size: 13px;'>");
        html.append("    <thead>");
        html.append("      <tr style='background-color: #1e40af; color: #ffffff; font-weight: bold;'>");
        html.append("        <th style='text-align: left; padding: 8px 10px; width: 70%;'>Bed Type</th>");
        html.append("        <th style='text-align: right; padding: 8px 10px; width: 30%;'>Occupancy Rate</th>");
        html.append("      </tr>");
        html.append("    </thead>");
        html.append("    <tbody>");

        double grandOccupied = 0;
        int rowIdx = 0;

        for (Map.Entry<String, List<Map<String, Object>>> entry : wardRows.entrySet()) {
            String wardName = entry.getKey();
            List<Map<String, Object>> wList = entry.getValue();

            double wardOccupied = 0;
            double wardBeds = 0;
            double wardDays = 0;

            for (Map<String, Object> r : wList) {
                wardOccupied += ((Number) r.getOrDefault("occupied_days", 0)).doubleValue();
                wardBeds = ((Number) r.getOrDefault("total_beds", 0)).doubleValue();
                wardDays += ((Number) r.getOrDefault("num_days", 30)).doubleValue();
            }

            double wardRate = wardBeds > 0 && wardDays > 0 ? (wardOccupied * 100.0) / (wardBeds * wardDays) : 0.0;
            String bgColor = (rowIdx % 2 == 0) ? "#ffffff" : "#f8fafc";

            html.append("      <tr style='background-color: ").append(bgColor).append("; border-bottom: 1px solid #eee;'>");
            html.append("        <td style='text-align: left; padding: 8px 10px;'>").append(wardName).append("</td>");
            html.append("        <td style='text-align: right; padding: 8px 10px;'>").append(String.format(Locale.US, "%.2f%%", wardRate)).append("</td>");
            html.append("      </tr>");

            grandOccupied += wardOccupied;
            rowIdx++;
        }

        double totalUniqueBeds = 0;
        for (Map.Entry<String, List<Map<String, Object>>> entry : wardRows.entrySet()) {
            List<Map<String, Object>> wList = entry.getValue();
            if (!wList.isEmpty()) {
                totalUniqueBeds += ((Number) wList.get(0).getOrDefault("total_beds", 0)).doubleValue();
            }
        }

        double totalYearDays = 0;
        if (!rows.isEmpty()) {
            Map<String, Double> periodDaysMap = new HashMap<>();
            for (Map<String, Object> r : rows) {
                periodDaysMap.put((String) r.get("period"), ((Number) r.getOrDefault("num_days", 30)).doubleValue());
            }
            for (double d : periodDaysMap.values()) {
                totalYearDays += d;
            }
        }
        if (totalYearDays == 0) totalYearDays = 365;

        double grandRate = totalUniqueBeds > 0 ? (grandOccupied * 100.0) / (totalUniqueBeds * totalYearDays) : 0.0;

        html.append("      <tr style='border-top: 1px dashed #bbb; border-bottom: 1px dashed #bbb; font-weight: bold;'>");
        html.append("        <td style='text-align: left; padding: 10px 10px;'>Grand Total :</td>");
        html.append("        <td style='text-align: right; padding: 10px 10px;'>").append(String.format(Locale.US, "%.2f%%", grandRate)).append("</td>");
        html.append("      </tr>");
        html.append("    </tbody>");
        html.append("  </table>");
        html.append("</div>");

        // Bed Occupancy Detail Table
        html.append("<div style='margin-bottom: 30px;'>");
        html.append("  <h3 style='margin: 20px 0 10px 0; font-size: 14px; font-weight: bold; color: #111;'>Bed Occupancy Detail</h3>");
        html.append("  <table style='width: 100%; border-collapse: collapse; font-size: 13px;'>");
        html.append("    <thead>");
        html.append("      <tr style='background-color: #1e40af; color: #ffffff; font-weight: bold;'>");
        html.append("        <th style='text-align: left; padding: 8px 10px; width: 70%;'>Bed Type</th>");
        html.append("        <th style='text-align: right; padding: 8px 10px; width: 30%;'>Occupancy Rate</th>");
        html.append("      </tr>");
        html.append("    </thead>");
        html.append("    <tbody>");

        String[] monthNames = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        };

        for (int m = 1; m <= maxMonth; m++) {
            String monthKey = String.format("%s-%02d", year, m);
            String monthName = monthNames[m - 1];
            List<Map<String, Object>> list = monthRows.get(monthKey);

            // Month Name Row (Full width)
            html.append("      <tr>");
            html.append("        <td colspan='2' style='text-align: left; font-weight: bold; padding: 12px 10px 6px 10px; font-size: 13px; color: #111;'>").append(monthName).append("</td>");
            html.append("      </tr>");

            double totalOccupied = 0;
            double totalBeds = 0;
            for (Map<String, Object> r : list) {
                totalOccupied += ((Number) r.getOrDefault("occupied_days", 0)).doubleValue();
                totalBeds += ((Number) r.getOrDefault("total_beds", 0)).doubleValue();
            }

            if (totalOccupied == 0 || list.isEmpty()) {
                html.append("      <tr>");
                html.append("        <td colspan='2' style='color: #ef4444; font-style: italic; font-weight: bold; font-size: 13px; padding: 6px 10px;'>");
                html.append("          No Record Found !!! There is no Bed Occupied for ").append(monthName);
                html.append("        </td>");
                html.append("      </tr>");
            } else {
                int monthRowIdx = 0;
                for (Map<String, Object> r : list) {
                    String ward = (String) r.get("ward");
                    Number pct = (Number) r.getOrDefault("occupancy_pct", 0.0);
                    String mBgColor = (monthRowIdx % 2 == 0) ? "#ffffff" : "#f8fafc";
                    
                    html.append("      <tr style='background-color: ").append(mBgColor).append(";'>");
                    html.append("        <td style='text-align: left; padding: 6px 10px;'>").append(ward).append("</td>");
                    html.append("        <td style='text-align: right; padding: 6px 10px;'>").append(String.format(Locale.US, "%.2f%%", pct.doubleValue())).append("</td>");
                    html.append("      </tr>");
                    monthRowIdx++;
                }

                // Month Total row
                Number firstRowDays = list.isEmpty() ? 30 : (Number) list.get(0).getOrDefault("num_days", 30);
                double numDays = firstRowDays.doubleValue();
                double totalPct = totalBeds > 0 ? (totalOccupied * 100.0) / (totalBeds * numDays) : 0.0;

                html.append("      <tr style='border-top: 1px dashed #bbb; border-bottom: 1px dashed #bbb; font-weight: bold;'>");
                html.append("        <td style='text-align: left; padding: 8px 10px;'>Total</td>");
                html.append("        <td style='text-align: right; padding: 8px 10px;'>").append(String.format(Locale.US, "%.2f%%", totalPct)).append("</td>");
                html.append("      </tr>");
            }
        }

        html.append("    </tbody>");
        html.append("  </table>");
        html.append("</div>");

        // Footer table info (matching generated by, generated date, and page info)
        String generatedDate = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm a").format(new Date());
        html.append("<table style='width: 100%; font-size: 11px; color: #666; margin-top: 30px; border-top: 1px solid #eee; padding-top: 10px;'>");
        html.append("  <tr>");
        html.append("    <td style='text-align: left; width: 33%;'>Generated By : App Admin</td>");
        html.append("    <td style='text-align: center; width: 33%;'>").append(generatedDate).append("</td>");
        html.append("    <td style='text-align: right; width: 33%;'>Page 1 of 1</td>");
        html.append("  </tr>");
        html.append("</table>");

        html.append("</div>");
        return html.toString();
    }
}
