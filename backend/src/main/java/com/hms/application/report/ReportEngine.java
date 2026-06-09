package com.hms.application.report;

import com.hms.application.attachment.AttachmentService;
import com.hms.domain.attachment.model.Attachment;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Component
@Slf4j
public class ReportEngine {

    private final SettingsRegistryImpl settingsRegistry;
    private final AttachmentService attachmentService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public ReportEngine(SettingsRegistryImpl settingsRegistry, AttachmentService attachmentService, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.settingsRegistry = settingsRegistry;
        this.attachmentService = attachmentService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public static final String REPORT_CSS =
        "body{font-family:'Segoe UI',sans-serif;font-size:12px;color:#1e293b;margin:0}" +
        "table{border-collapse:collapse;width:100%;font-size:12px}" +
        "thead{display:table-header-group}" +
        "thead tr{background:#525252;color:#fff}" +
        "th{padding:8px 10px;text-align:left;white-space:nowrap;font-weight:600}" +
        "td{padding:6px 10px;border-bottom:1px solid #e2e8f0;white-space:nowrap;text-align:left}" +
        "tr{page-break-inside:avoid}" +
        "tr:nth-child(even){background:#f8fafc}" +
        "tr:hover td{background:#f1f5f9}" +
        ".summary{padding:10px;background:#f1f5f9;border-radius:4px;margin-bottom:8px;font-size:11px;color:#475569}" +
        ".page-break{page-break-before:always}";

    public String executeAsHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if (rows.isEmpty()) {
            return "<p style='padding:16px;color:#64748b;font-family:sans-serif'>No data found for the selected parameters.</p>";
        }

        boolean isEmptyRow = rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__"));

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary'>")
          .append("<strong>").append(escHtml(humanise(reportName))).append("</strong>")
          .append(" &nbsp;|&nbsp; ").append(isEmptyRow ? 0 : rows.size()).append(" record(s)")
          .append("</div>");

        Set<String> cols = new java.util.LinkedHashSet<>(rows.get(0).keySet());
        cols.remove("consultant_id");
        cols.remove("department_id");
        cols.remove("__EMPTY_ROW__");
        cols.remove("Qty");
        cols.remove("Free Qty");

        // ── Merge Age + Sex/Gender into a single "Age/Sex" column ──
        String ageKey    = cols.contains("Age") ? "Age" : null;
        String sexKey    = cols.contains("Sex") ? "Sex" : cols.contains("Gender") ? "Gender" : null;
        boolean mergeAgeSex = ageKey != null && sexKey != null;
        if (mergeAgeSex) {
            // Build a new ordered set with the merged column placed where Age appeared
            Set<String> merged = new java.util.LinkedHashSet<>();
            for (String c : cols) {
                if (c.equals(ageKey)) { merged.add("Age/Sex"); }
                else if (!c.equals(sexKey)) { merged.add(c); }
            }
            cols = merged;
        }
        final String finalAgeKey = ageKey;
        final String finalSexKey = sexKey;
        final boolean finalMerge = mergeAgeSex;

        sb.append("<table><thead><tr>");
        cols.forEach(c -> sb.append("<th>").append(escHtml(humanise(c))).append("</th>"));
        sb.append("</tr></thead><tbody>");
        
        if (isEmptyRow) {
            sb.append("<tr><td colspan='").append(cols.size()).append("' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            for (Map<String, Object> row : rows) {
                sb.append("<tr>");
                cols.forEach(c -> {
                    if (finalMerge && "Age/Sex".equals(c)) {
                        String age = formatGeneralValue(row.get(finalAgeKey));
                        // Strip trailing " Y" if present e.g. "34 Y" -> "34"
                        age = age.replaceAll("\\s*Y$", "").trim();
                        String ageVal = age.isEmpty() ? "-" : age;
                        String sexFull = formatGeneralValue(row.get(finalSexKey)).toUpperCase();
                        String sex = sexFull.isEmpty() ? "-" :
                            sexFull.startsWith("M") ? "M" :
                            sexFull.startsWith("F") ? "F" : "-";
                        sb.append("<td>").append(escHtml(ageVal + "/" + sex)).append("</td>");
                    } else {
                        Object v = row.get(c);
                        String valStr = formatGeneralValue(v);
                        sb.append("<td>").append(valStr.isEmpty() ? "" : escHtml(valStr)).append("</td>");
                    }
                });
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table>");
        return sb.toString();
    }

    public byte[] generatePdfFromHtml(String reportName, String htmlContent,
                                       String reportDescription, Map<String, Object> params) {
        String extractedStyles = "";
        String cleanHtmlContent = htmlContent;
        if (htmlContent != null) {
            java.util.regex.Pattern stylePattern = java.util.regex.Pattern.compile("(?s)<style[^>]*>(.*?)</style>");
            java.util.regex.Matcher styleMatcher = stylePattern.matcher(htmlContent);
            StringBuilder sbStyles = new StringBuilder();
            while (styleMatcher.find()) {
                sbStyles.append(styleMatcher.group(1)).append("\n");
            }
            extractedStyles = sbStyles.toString();
            cleanHtmlContent = styleMatcher.replaceAll("");
        }

        String headerHtml = buildReportHeaderHtml(reportDescription, params);
        
        // Strip any pre-existing h2 headers from custom builders to ensure a single, uniform title
        if (cleanHtmlContent != null) {
            cleanHtmlContent = cleanHtmlContent.replaceAll("(?is)<h2[^>]*>.*?</h2>", "");
        }
        
        // Always prepend the report title header in a uniform style
        String title = (reportDescription != null && !reportDescription.isEmpty()) ? reportDescription : humanise(reportName);
        if (cleanHtmlContent != null) {
            cleanHtmlContent = "<h2 class='report-title' style='font-size:18px;font-weight:700;color:#1e293b;margin:0 0 10px 0;font-family:sans-serif;'>" +
                               escHtml(title) + "</h2>" + cleanHtmlContent;
        }
        
        // Inject search criteria under the body's main header
        String contentWithCriteria = injectSearchCriteriaInBody(cleanHtmlContent, params);

        String fullHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>" +
                          "@page { size: A4 landscape; margin-top: 28mm; margin-bottom: 15mm; margin-left: 15mm; margin-right: 15mm; @top-right { content: element(header); } }" +
                          REPORT_CSS +
                          ".report-header{position: running(header); width: 100%; font-family:'Segoe UI',sans-serif; border-bottom: 1px solid #cbd5e1; padding-bottom: 8px;}" +
                          ".report-header table{border:none;margin-bottom:0;width:auto;margin-left:auto;margin-right:0}" +
                          ".report-header td{border:none;padding:0;background:none}" +
                          ".hospital-name{font-size:16px;font-weight:700;color:#1e293b;margin:0;text-align:right}" +
                          ".hospital-address{font-size:11px;color:#475569;margin:2px 0;text-align:right}" +
                          ".hospital-contact{font-size:11px;color:#475569;margin:2px 0;text-align:right}" +
                          extractedStyles +
                          "</style></head><body>" +
                          headerHtml +
                          contentWithCriteria +
                          "</body></html>";
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(fullHtml);
            renderer.layout();
            renderer.createPDF(os);
            return os.toByteArray();
        } catch (Exception ex) {
            log.error("Failed to generate PDF for {}: {}", reportName, ex.getMessage());
            throw new com.hms.exception.BusinessRuleViolationException("PDF generation failed: " + ex.getMessage());
        }
    }

    /** Backward-compatible overload (no header) */
    public byte[] generatePdfFromHtml(String reportName, String htmlContent) {
        return generatePdfFromHtml(reportName, htmlContent, null, null);
    }

    private String injectSearchCriteriaInBody(String htmlContent, Map<String, Object> params) {
        String criteria = formatSearchCriteria(params);
        if (criteria.isEmpty() || htmlContent == null) return htmlContent;

        String criteriaHtml = "<div class='report-criteria-sub' style='font-size:11px;color:#475569;margin-top:6px;margin-bottom:15px;font-style:italic;text-align:left;font-family:sans-serif;'>" +
                              escHtml(criteria) + "</div>";

        // Case 1: Custom report header: <h2 class='report-title'>...</h2> or similar
        int h2EndIdx = htmlContent.toLowerCase().indexOf("</h2>");
        if (h2EndIdx != -1) {
            return htmlContent.substring(0, h2EndIdx + 5) + "\n" + criteriaHtml + htmlContent.substring(h2EndIdx + 5);
        }

        // Case 2: Generic report header: <div class='summary'>...</div>
        int divEndIdx = htmlContent.toLowerCase().indexOf("</div>");
        if (divEndIdx != -1 && htmlContent.toLowerCase().contains("class='summary'")) {
            return htmlContent.substring(0, divEndIdx + 6) + "\n" + criteriaHtml + htmlContent.substring(divEndIdx + 6);
        }

        // Fallback: prepend
        return criteriaHtml + htmlContent;
    }

    public String paginateHtmlString(String html) {
        if (html == null) return null;

        int tbodyStart = html.indexOf("<tbody>");
        int tbodyEnd = html.indexOf("</tbody>");
        if (tbodyStart == -1 || tbodyEnd == -1) {
            return html;
        }

        String prefix = html.substring(0, tbodyStart + 7);
        String body = html.substring(tbodyStart + 7, tbodyEnd);
        String suffix = html.substring(tbodyEnd);

        java.util.regex.Pattern p = java.util.regex.Pattern.compile("<tr(?:\\s[^>]*)?>");
        java.util.regex.Matcher m = p.matcher(body);
        StringBuilder newBody = new StringBuilder();
        int lastEnd = 0;
        int trCount = 0;
        while (m.find()) {
            newBody.append(body, lastEnd, m.start());
            String tag = m.group();
            trCount++;
            if (trCount > 1 && (trCount - 1) % 15 == 0) {
                if (tag.contains("style=")) {
                    tag = tag.replace("style='", "style='page-break-before:always; ");
                    tag = tag.replace("style=\"", "style=\"page-break-before:always; ");
                } else if (tag.contains("class=")) {
                    tag = tag.replace("class='", "class='page-break ");
                    tag = tag.replace("class=\"", "class=\"page-break ");
                } else {
                    tag = tag.replace("<tr", "<tr class='page-break'");
                }
            }
            newBody.append(tag);
            lastEnd = m.end();
        }
        newBody.append(body.substring(lastEnd));
        return prefix + newBody.toString() + suffix;
    }

    public String buildCsv(List<Map<String, Object>> rows, String reportDescription, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();

        // ── Header rows ──
        Map<String, String> hospitalParams = settingsRegistry.getValueMapByType("HOSPITAL_PARAM");
        String hospitalName = hospitalParams.getOrDefault("hospital.name.param", "HMS Hospital");
        String hospitalAddress = hospitalParams.getOrDefault("hospital.address.param", "");
        sb.append(csvQuote(hospitalName)).append("\n");
        if (!hospitalAddress.isEmpty()) {
            sb.append(csvQuote(hospitalAddress)).append("\n");
        }
        sb.append("\n"); // blank row
        if (reportDescription != null && !reportDescription.isEmpty()) {
            sb.append(csvQuote(reportDescription)).append("\n");
        }
        String criteria = formatSearchCriteria(params);
        if (!criteria.isEmpty()) {
            sb.append(csvQuote(criteria)).append("\n");
        }
        sb.append("\n"); // blank row before data

        if (rows.isEmpty()) return sb.toString();

        boolean isEmptyRow = rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__"));

        Set<String> cols = new java.util.LinkedHashSet<>(rows.get(0).keySet());
        cols.remove("consultant_id");
        cols.remove("department_id");
        cols.remove("__EMPTY_ROW__");
        cols.remove("Qty");
        cols.remove("Free Qty");

        List<String> humanisedCols = cols.stream().map(this::humanise).toList();
        sb.append(String.join(",", humanisedCols)).append("\n");
        
        if (!isEmptyRow) {
            for (Map<String, Object> row : rows) {
                StringJoiner sj = new StringJoiner(",");
                cols.forEach(c -> {
                    Object v = row.get(c);
                    String valStr = formatGeneralValue(v);
                    String s = valStr.replace("\"", "\"\"");
                    sj.add("\"" + s + "\"");
                });
                sb.append(sj).append("\n");
            }
        }
        return sb.toString();
    }

    /** Backward-compatible overload (no header) */
    public String buildCsv(List<Map<String, Object>> rows) {
        return buildCsv(rows, null, null);
    }

    private String csvQuote(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ── Report header builders ──────────────────────────────────────────────

    /**
     * Builds an HTML header block for PDF reports with hospital logo, name, address,
     * report title, and search criteria.
     */
    public String buildReportHeaderHtml(String reportDescription, Map<String, Object> params) {
        Map<String, String> hospitalParams = settingsRegistry.getValueMapByType("HOSPITAL_PARAM");
        String hospitalName = escHtml(hospitalParams.getOrDefault("hospital.name.param", "HMS Hospital"));
        String hospitalAddress = escHtml(hospitalParams.getOrDefault("hospital.address.param", ""));
        String hospitalContact = escHtml(hospitalParams.getOrDefault("hospital.contactNo.param", ""));

        // Try to load hospital logo as base64
        String logoImgTag = "";
        try {
            Optional<Attachment> logoOpt = attachmentService.getLatestByCategory("HOSPITAL_LOGO");
            if (logoOpt.isPresent()) {
                Attachment logo = logoOpt.get();
                Path logoPath = Paths.get(logo.getFilePath());
                if (Files.exists(logoPath)) {
                    byte[] logoBytes = Files.readAllBytes(logoPath);
                    String base64 = Base64.getEncoder().encodeToString(logoBytes);
                    String mimeType = logo.getContentType() != null ? logo.getContentType() : "image/jpeg";
                    logoImgTag = "<img src='data:" + mimeType + ";base64," + base64 +
                                 "' width='60' height='60' />";
                }
            }
        } catch (Exception e) {
            log.debug("Could not load hospital logo for report: {}", e.getMessage());
        }

        StringBuilder hdr = new StringBuilder();
        hdr.append("<div class='report-header'>");
        hdr.append("<table style='width:auto;margin-left:auto;margin-right:0;'>");
        hdr.append("<tr>");
        // Left column: logo (aligned right, next to details)
        if (!logoImgTag.isEmpty()) {
            hdr.append("<td style='vertical-align:middle;text-align:right;padding-right:12px;width:60px'>");
            hdr.append(logoImgTag);
            hdr.append("</td>");
        }
        // Right column: hospital details (text right aligned)
        hdr.append("<td style='vertical-align:middle;text-align:right'>");
        hdr.append("<div class='hospital-name'>").append(hospitalName).append("</div>");
        if (!hospitalAddress.isEmpty()) {
            hdr.append("<div class='hospital-address'>").append(hospitalAddress).append("</div>");
        }
        if (!hospitalContact.isEmpty()) {
            hdr.append("<div class='hospital-contact'>Contact: ").append(hospitalContact).append("</div>");
        }
        hdr.append("</td>");
        hdr.append("</tr>");
        hdr.append("</table>");

        // Report title removed from running header

        hdr.append("</div>");
        return hdr.toString();
    }

    /**
     * Formats the search params map into a human-readable string for display.
     * E.g. "From Date: 01/06/2026 | To Date: 08/06/2026 | Item: All"
     */
    public String formatSearchCriteria(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";
        StringJoiner sj = new StringJoiner("  |  ");
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (val == null || val.toString().trim().isEmpty()) continue;
            // Skip internal/meta params
            if (key.startsWith("__") || key.equals("report_view_type") || key.equals("report_type")
                || key.equals("department_filter") || key.equals("po_no_filter")
                || key.equals("grn_no_filter") || key.equals("return_no_filter")
                || key.equals("bed_type_filter")) continue;

            String label = humanise(key);
            if ("itemId".equalsIgnoreCase(key) || "item_id".equalsIgnoreCase(key)) {
                label = "Item";
            } else if ("consultantId".equalsIgnoreCase(key) || "consultant_id".equalsIgnoreCase(key)) {
                label = "Consultant";
            }
            String value = val.toString().trim();

            // Format dates nicely
            if (key.contains("date") && value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                try {
                    java.time.LocalDate d = java.time.LocalDate.parse(value);
                    value = d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } catch (Exception ex) {
                    // keep raw
                }
            }

            // Mask UUIDs / Resolve names
            if (value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                if ("consultantId".equalsIgnoreCase(key) || "consultant_id".equalsIgnoreCase(key)) {
                    try {
                        String name = jdbcTemplate.queryForObject(
                            "SELECT COALESCE(first_name || ' ' || last_name || COALESCE(' (' || qualification || ')', ''), '') FROM consultants WHERE id = ?::uuid",
                            String.class,
                            value
                        );
                        if (name != null && !name.trim().isEmpty()) {
                            value = name;
                        } else {
                            value = "Selected";
                        }
                    } catch (Exception ex) {
                        value = "Selected";
                    }
                } else if ("itemId".equalsIgnoreCase(key) || "item_id".equalsIgnoreCase(key)) {
                    try {
                        String name = jdbcTemplate.queryForObject(
                            "SELECT name FROM inventory_items WHERE id = ?::uuid",
                            String.class,
                            value
                        );
                        if (name != null && !name.trim().isEmpty()) {
                            value = name;
                        } else {
                            value = "Selected";
                        }
                    } catch (Exception ex) {
                        value = "Selected";
                    }
                } else {
                    value = "Selected";
                }
            } else if (value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*")) {
                value = "Selected";
            }

            sj.add(label + ": " + value);
        }
        return sj.toString();
    }

    // Formatting utilities
    public String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v != null ? v.toString() : "";
    }

    public String dateStr(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null || v.toString().trim().isEmpty()) {
            return java.time.LocalDate.now().toString();
        }
        return v.toString().trim();
    }

    public UUID uuid(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) return null;
        try { return UUID.fromString(v.toString()); } catch (Exception e) { return null; }
    }

    public String humanise(String snake) {
        if ("purchase_orders_report".equalsIgnoreCase(snake)) {
            return "Purchase Order Report";
        }
        if ("po_date".equalsIgnoreCase(snake)) {
            return "PO Date";
        }
        if ("po_no".equalsIgnoreCase(snake)) {
            return "PO No";
        }
        if ("grn_no".equalsIgnoreCase(snake)) {
            return "GRN No";
        }
        if ("grn_value".equalsIgnoreCase(snake)) {
            return "GRN Value";
        }
        if ("grn_date".equalsIgnoreCase(snake)) {
            return "GRN Date";
        }
        if ("return_no".equalsIgnoreCase(snake)) {
            return "Return No";
        }
        if ("reason_for_goods_return".equalsIgnoreCase(snake)) {
            return "Reason for Goods Return";
        }
        if ("total_purchase_value".equalsIgnoreCase(snake)) {
            return "Total purchase value";
        }
        if ("mrp".equalsIgnoreCase(snake)) {
            return "MRP";
        }
        String h = Arrays.stream(snake.split("_"))
            .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
            .collect(java.util.stream.Collectors.joining(" "));
        if (h.equalsIgnoreCase("Patient Name") || h.equalsIgnoreCase("PatientName")) {
            return "Patient";
        }
        if (h.equalsIgnoreCase("Patient Number") || h.equalsIgnoreCase("Patient No") || h.equalsIgnoreCase("Patientno") || h.equalsIgnoreCase("Patientnumber")) {
            return "Patient No";
        }
        if (h.equalsIgnoreCase("Bill Number") || h.equalsIgnoreCase("Bill No") || h.equalsIgnoreCase("Billno") || h.equalsIgnoreCase("Billnumber")) {
            return "Bill No";
        }
        if (h.equalsIgnoreCase("Sale Number") || h.equalsIgnoreCase("Sale No") || h.equalsIgnoreCase("Saleno") || h.equalsIgnoreCase("Salenumber")) {
            return "Sale No";
        }
        if (h.equalsIgnoreCase("Customer Name") || h.equalsIgnoreCase("Customername")) {
            return "Customer";
        }
        h = h.replaceAll("(?i)\\bVisits\\b", "Encounters");
        h = h.replaceAll("(?i)\\bVisit\\b", "Encounter");
        return h;
    }

    public String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    public double doubleVal(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    public int toInt(Object obj) {
        if (obj == null) return 0;
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return 0; }
    }

    public double toDouble(Object obj) {
        return doubleVal(obj);
    }

    public String formatValue(Object val, boolean isDiscount) {
        if (val == null) return isDiscount ? "-" : "0";
        double d;
        if (val instanceof Number) {
            d = ((Number) val).doubleValue();
        } else {
            String s = val.toString().trim();
            if (s.isEmpty() || s.equals("0E-20") || s.equals("0.0") || s.equals("0.00") || s.equals("0")) {
                return isDiscount ? "-" : "0";
            }
            try {
                d = Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return s;
            }
        }

        if (Math.abs(d) < 0.0001) {
            return isDiscount ? "-" : "0";
        }

        if (d == Math.floor(d)) {
            return String.format(java.util.Locale.US, "%.0f", d);
        } else {
            return String.format(java.util.Locale.US, "%.2f", d);
        }
    }

    public String formatGeneralValue(Object v) {
        if (v == null) return "";
        if (v instanceof java.sql.Date || v instanceof java.util.Date) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy").format((java.util.Date) v);
        }
        if (v instanceof java.time.LocalDate) {
            return ((java.time.LocalDate) v).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        if (v instanceof java.sql.Timestamp) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format((java.util.Date) v);
        }
        if (v instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) v).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        }
        if (v instanceof java.time.Instant) {
            return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format((java.time.Instant) v);
        }
        if (v instanceof java.math.BigDecimal) {
            java.math.BigDecimal bd = (java.math.BigDecimal) v;
            bd = bd.stripTrailingZeros();
            String s = bd.toPlainString();
            if (s.equals("0E-20")) return "0";
            return s;
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.floor(d)) {
                return String.format(java.util.Locale.US, "%.0f", d);
            } else {
                return String.format(java.util.Locale.US, "%.2f", d);
            }
        }
        return v.toString();
    }

    public String formatDateValue(Object dateObj) {
        if (dateObj instanceof java.sql.Date || dateObj instanceof java.util.Date) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy").format(dateObj);
        } else if (dateObj instanceof java.time.LocalDate) {
            return ((java.time.LocalDate) dateObj).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } else if (dateObj instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) dateObj).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } else if (dateObj instanceof java.time.OffsetDateTime) {
            return ((java.time.OffsetDateTime) dateObj).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } else if (dateObj instanceof java.time.Instant) {
            return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format((java.time.Instant) dateObj);
        } else if (dateObj != null) {
            String s = dateObj.toString();
            if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
                try {
                    String[] parts = s.substring(0, 10).split("-");
                    return parts[2] + "/" + parts[1] + "/" + parts[0];
                } catch (Exception e) {
                    // ignore
                }
            }
            return s;
        }
        return "";
    }
}
