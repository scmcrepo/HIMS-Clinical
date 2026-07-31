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
        List<Map<String, Object>> rows = decryptQueryResult(executeDataQuery(reportName, params));
        
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
        List<Map<String, Object>> rows = decryptQueryResult(executeDataQuery(reportName, params));
        
        List<Map<String, Object>> strippedRows = new ArrayList<>(rows);
        if (strippedRows.size() == 1 && Boolean.TRUE.equals(strippedRows.get(0).get("__EMPTY_ROW__"))) {
            strippedRows.clear();
        }

        // Look up report description for header
        String reportDescription = "";
        try {
            Map<String, Object> info = getReportInfo(reportName);
            Object desc = info.get("description");
            if (desc != null) reportDescription = desc.toString();
        } catch (Exception e) {
            // ignore — will just skip the title
        }

        List<Map<String, Object>> exportRows = getExportRows(reportName, rows, params);

        if ("CSV".equals(format)) {
            return reportEngine.buildCsv(exportRows, reportDescription, params).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if ("XLSX".equals(format)) {
            return reportEngine.buildXlsx(exportRows, reportDescription, params);
        }
        if ("PDF".equals(format)) {
            // Build HTML content WITHOUT the <style> prefix — generatePdfFromHtml adds CSS in <head>
            String customHtml = buildCustomHtml(reportName, strippedRows, params);
            String htmlContent = customHtml != null ? customHtml : reportEngine.executeAsHtml(reportName, rows, params);

            // Sanitize HTML for PDF: strip JS artifacts that crash the XHTML renderer
            htmlContent = sanitizeHtmlForPdf(htmlContent);

            htmlContent = reportEngine.paginateHtmlString(htmlContent);
            return reportEngine.generatePdfFromHtml(reportName, htmlContent, reportDescription, params);
        }
        throw new com.hms.exception.BusinessRuleViolationException("Unsupported format: " + format);
    }

    public List<Map<String, Object>> executeAsJson(String reportName, Map<String, Object> params) {
        List<Map<String, Object>> rows = decryptQueryResult(executeDataQuery(reportName, params));
        if (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__"))) {
            return Collections.emptyList();
        }
        return rows;
    }

    protected List<Map<String, Object>> decryptQueryResult(List<Map<String, Object>> rows) {
        if (rows == null) return null;
        List<Map<String, Object>> decryptedRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row != null) {
                Map<String, Object> decryptedRow = new LinkedHashMap<>(row);
                decryptMap(decryptedRow);
                decryptedRows.add(decryptedRow);
            } else {
                decryptedRows.add(null);
            }
        }
        return decryptedRows;
    }

    private void decryptMap(Map<String, Object> row) {
        if (row == null) return;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String) {
                entry.setValue(reportEngine.decryptFormatted((String) val));
            } else if (val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = new LinkedHashMap<>((Map<String, Object>) val);
                decryptMap(childMap);
                entry.setValue(childMap);
            } else if (val instanceof List) {
                List<Object> newList = new ArrayList<>();
                for (Object item : (List<?>) val) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> childMap = new LinkedHashMap<>((Map<String, Object>) item);
                        decryptMap(childMap);
                        newList.add(childMap);
                    } else {
                        newList.add(item);
                    }
                }
                entry.setValue(newList);
            }
        }
    }

    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        return null; // fallback to generic table
    }

    protected List<Map<String, Object>> getExportRows(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        return rows;
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

    /**
     * Strips JavaScript artifacts from custom HTML so the XHTML PDF renderer
     * (Flying Saucer / ITextRenderer) can process it without errors.
     *
     * Removes: <img ... onerror="..."> tags, onclick attributes,
     *          the hidden detail-view div, and <button> elements.
     */
    private String sanitizeHtmlForPdf(String html) {
        if (html == null) return null;

        // 1. If this is the multi-section net collection report, make detail-view sections visible for static PDF
        if (html.contains("id=\"detail-view\"") || html.contains("id='detail-view'")) {
            html = html.replaceAll("(?i)display\\s*:\\s*none;?", "display:block;");
        }

        // 2. Remove script tags if any
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");

        // 3. Remove onerror and onclick attributes completely (including quoted attribute contents)
        html = html.replaceAll("(?is)\\s+onerror\\s*=\\s*\"[^\"]*\"", "");
        html = html.replaceAll("(?is)\\s+onerror\\s*=\\s*'[^']*'", "");
        html = html.replaceAll("(?is)\\s+onclick\\s*=\\s*\"[^\"]*\"", "");
        html = html.replaceAll("(?is)\\s+onclick\\s*=\\s*'[^']*'", "");

        // 4. Remove <img> elements entirely
        html = html.replaceAll("(?is)<img\\b[^>]*>", "");

        // 5. Remove SVG elements completely (Flying Saucer XML parser fails on SVG/line/polyline)
        html = html.replaceAll("(?is)<svg[^>]*>.*?</svg>", "");

        // 6. Remove <button> elements entirely (not valid for static PDF)
        html = html.replaceAll("(?is)<button[^>]*>.*?</button>", "");

        // 7. Remove unsupported CSS3 properties that crash Flying Saucer (flexbox, gap, box-shadow)
        html = html.replaceAll("(?i)display\\s*:\\s*(inline-)?flex;?", "");
        html = html.replaceAll("(?i)gap\\s*:[^;\"]*;?", "");
        html = html.replaceAll("(?i)box-shadow\\s*:[^;\"]*;?", "");

        // 8. Remove <a> wrappers but keep inner text for summary links
        html = html.replaceAll("(?is)<a[^>]*class=['\"]summary-link['\"][^>]*>(.*?)</a>", "$1");

        return html;
    }
}
