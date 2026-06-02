package com.hms.application.report;

import java.util.*;

public abstract class BaseReportService {

    protected final ReportEngine reportEngine;

    protected BaseReportService(ReportEngine reportEngine) {
        this.reportEngine = reportEngine;
    }

    public abstract List<Map<String, String>> getAvailableReports();

    public abstract Map<String, Object> getReportInfo(String reportName);

    public abstract List<Map<String, Object>> executeDataQuery(String reportName, Map<String, Object> params);

    public String executeAsHtml(String reportName, Map<String, Object> params) {
        List<Map<String, Object>> rows = executeDataQuery(reportName, params);
        
        List<Map<String, Object>> strippedRows = new ArrayList<>(rows);
        if (strippedRows.size() == 1 && Boolean.TRUE.equals(strippedRows.get(0).get("__EMPTY_ROW__"))) {
            strippedRows.clear();
        }

        String customHtml = buildCustomHtml(reportName, strippedRows, params);
        if (customHtml != null) {
            return "<style>" + ReportEngine.REPORT_CSS + "</style>" + customHtml;
        }
        return "<style>" + ReportEngine.REPORT_CSS + "</style>" + reportEngine.executeAsHtml(reportName, rows, params);
    }

    public byte[] executeAsBinary(String reportName, Map<String, Object> params, String format) {
        List<Map<String, Object>> rows = executeDataQuery(reportName, params);
        
        List<Map<String, Object>> strippedRows = new ArrayList<>(rows);
        if (strippedRows.size() == 1 && Boolean.TRUE.equals(strippedRows.get(0).get("__EMPTY_ROW__"))) {
            strippedRows.clear();
        }

        if ("CSV".equals(format) || "XLSX".equals(format)) {
            return reportEngine.buildCsv(rows).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if ("PDF".equals(format)) {
            // Build HTML content WITHOUT the <style> prefix — generatePdfFromHtml adds CSS in <head>
            String customHtml = buildCustomHtml(reportName, strippedRows, params);
            String htmlContent = customHtml != null ? customHtml : reportEngine.executeAsHtml(reportName, rows, params);
            htmlContent = reportEngine.paginateHtmlString(htmlContent);
            return reportEngine.generatePdfFromHtml(reportName, htmlContent);
        }
        throw new com.hms.exception.BusinessRuleViolationException("Unsupported format: " + format);
    }

    public List<Map<String, Object>> executeAsJson(String reportName, Map<String, Object> params) {
        List<Map<String, Object>> rows = executeDataQuery(reportName, params);
        if (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__"))) {
            return Collections.emptyList();
        }
        return rows;
    }

    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        return null; // fallback to generic table
    }

    protected static Map<String, Object> param(String name, String type,
                                             boolean required, String defaultVal,
                                             String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name",         name);
        p.put("type",         type);
        p.put("required",     required);
        p.put("defaultValue", defaultVal);
        p.put("description",  description);
        return p;
    }

    protected static final List<Map<String, Object>> DATE_RANGE_PARAMS = List.of(
        param("from_date", "DATE", true,  "", "From date"),
        param("to_date",   "DATE", true,  "", "To date")
    );
}
