package com.hms.application.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Component
@Slf4j
public class ReportEngine {

    public static final String REPORT_CSS =
        "body{font-family:'Segoe UI',sans-serif;font-size:12px;color:#1e293b;margin:0}" +
        "table{border-collapse:collapse;width:100%;font-size:12px}" +
        "thead{display:table-header-group}" +
        "thead tr{background:#1e40af;color:#fff}" +
        "th{padding:8px 10px;text-align:left;white-space:nowrap;font-weight:600}" +
        "td{padding:6px 10px;border-bottom:1px solid #e2e8f0;white-space:nowrap;text-align:left}" +
        "tr{page-break-inside:avoid}" +
        "tr:nth-child(even){background:#f8fafc}" +
        "tr:hover td{background:#eff6ff}" +
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

    public byte[] generatePdfFromHtml(String reportName, String htmlContent) {
        String fullHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>" +
                          "@page { size: A4 landscape; margin: 15mm; }" +
                          REPORT_CSS +
                          "</style></head><body>" +
                          htmlContent +
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

    public String buildCsv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "";

        boolean isEmptyRow = rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__"));

        StringBuilder sb = new StringBuilder();
        Set<String> cols = new java.util.LinkedHashSet<>(rows.get(0).keySet());
        cols.remove("consultant_id");
        cols.remove("department_id");
        cols.remove("__EMPTY_ROW__");

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
