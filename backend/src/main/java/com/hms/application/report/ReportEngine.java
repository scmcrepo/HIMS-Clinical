package com.hms.application.report;

import com.hms.application.attachment.AttachmentService;
import com.hms.domain.attachment.model.Attachment;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.persistence.tenant.TenantJpaRepository;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.security.encryption.PiiEncryptionService;
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
    private final PiiEncryptionService piiEncryptionService;
    private final TenantJpaRepository tenantRepo;

    public ReportEngine(SettingsRegistryImpl settingsRegistry, AttachmentService attachmentService, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate, PiiEncryptionService piiEncryptionService, TenantJpaRepository tenantRepo) {
        this.settingsRegistry = settingsRegistry;
        this.attachmentService = attachmentService;
        this.jdbcTemplate = jdbcTemplate;
        this.piiEncryptionService = piiEncryptionService;
        this.tenantRepo = tenantRepo;
    }

    /**
     * Returns the current tenant's theme colour (#rrggbb) or the application
     * default (#525252) when no colour has been chosen.
     */
    public String getThemeColor() {
        try {
            UUID tenantId = TenantContext.get();
            if (tenantId != null) {
                String color = tenantRepo.findById(tenantId)
                    .map(TenantEntity::getThemeColor)
                    .orElse(null);
                if (color != null && color.matches("^#[0-9a-fA-F]{6}$")) {
                    return color;
                }
            }
        } catch (Exception e) {
            log.debug("Could not resolve tenant theme colour: {}", e.getMessage());
        }
        return "#525252";
    }

    public String getSoftThemeColor(String themeHex) {
        if (themeHex == null || !themeHex.startsWith("#") || themeHex.length() != 7) {
            return "#f8fafc";
        }
        try {
            int r = Integer.parseInt(themeHex.substring(1, 3), 16);
            int g = Integer.parseInt(themeHex.substring(3, 5), 16);
            int b = Integer.parseInt(themeHex.substring(5, 7), 16);
            int sr = (int) Math.round(255 * 0.95 + r * 0.05);
            int sg = (int) Math.round(255 * 0.95 + g * 0.05);
            int sb = (int) Math.round(255 * 0.95 + b * 0.05);
            return String.format("#%02x%02x%02x", sr, sg, sb);
        } catch (Exception ignored) {
            return "#f8fafc";
        }
    }

    public String getHoverThemeColor(String themeHex) {
        if (themeHex == null || !themeHex.startsWith("#") || themeHex.length() != 7) {
            return "#f1f5f9";
        }
        try {
            int r = Integer.parseInt(themeHex.substring(1, 3), 16);
            int g = Integer.parseInt(themeHex.substring(3, 5), 16);
            int b = Integer.parseInt(themeHex.substring(5, 7), 16);
            int hr = (int) Math.round(255 * 0.92 + r * 0.08);
            int hg = (int) Math.round(255 * 0.92 + g * 0.08);
            int hb = (int) Math.round(255 * 0.92 + b * 0.08);
            return String.format("#%02x%02x%02x", hr, hg, hb);
        } catch (Exception ignored) {
            return "#f1f5f9";
        }
    }

    public String getReportCss() {
        String themeHex = getThemeColor();
        String softHex = getSoftThemeColor(themeHex);
        String hoverHex = getHoverThemeColor(themeHex);

        return "body{font-family:'Segoe UI',sans-serif;font-size:12px;color:#1e293b;margin:0}" +
               "table{border-collapse:collapse;width:100%;font-size:12px;page-break-inside:auto}" +
               "thead{display:table-header-group}" +
               "thead tr{background:" + themeHex + ";color:#fff}" +
               "th{padding:8px 10px;text-align:left;white-space:nowrap;font-weight:600;background:" + themeHex + ";color:#fff}" +
               "td{padding:6px 10px;border-bottom:1px solid #e2e8f0;white-space:nowrap;text-align:left}" +
               "tr{page-break-inside:avoid}" +
               "tr:nth-child(even){background:" + softHex + "}" +
               "tr.even td, tr.report-row-even td{background:" + softHex + "}" +
               "tr.even, tr.report-row-even{background:" + softHex + "}" +
               "tr:hover td{background:" + hoverHex + "}" +
               ".summary{padding:10px;background:" + softHex + ";border-left:3px solid " + themeHex + ";border-radius:4px;margin-bottom:8px;font-size:11px;color:#475569}" +
               ".page-break{page-break-before:always}";
    }

    public static final String REPORT_CSS =
        "body{font-family:'Segoe UI',sans-serif;font-size:12px;color:#1e293b;margin:0}" +
        "table{border-collapse:collapse;width:100%;font-size:12px;page-break-inside:auto}" +
        "thead{display:table-header-group}" +
        "thead tr{background:#525252;color:#fff}" +
        "th{padding:8px 10px;text-align:left;white-space:nowrap;font-weight:600}" +
        "td{padding:6px 10px;border-bottom:1px solid #e2e8f0;white-space:nowrap;text-align:left}" +
        "tr{page-break-inside:avoid}" +
        "tr:nth-child(even){background:#f8fafc}" +
        "tr:hover td{background:#f1f5f9}" +
        ".summary{padding:10px;background:#f1f5f9;border-radius:4px;margin-bottom:8px;font-size:11px;color:#475569}" +
        ".page-break{page-break-before:always}";

    public boolean isNarrowColumn(String colName) {
        if (colName == null) return false;
        String s = colName.trim().toLowerCase();
        return s.equals("sno") || s.equals("s.no") || s.equals("s.no.") || s.equals("s_no") || s.equals("s no")
            || s.equals("sl no") || s.equals("sl.no") || s.equals("sl_no") || s.equals("serial no")
            || s.equals("sr no") || s.equals("sr.no") || s.equals("#");
    }

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
        cols.remove("patient_id");
        cols.remove("encounter_id");
        cols.remove("supplier_id");
        cols.remove("user_id");
        cols.remove("item_id");
        cols.remove("po_notes");
        cols.remove("__EMPTY_ROW__");
        cols.remove("Chargeable Qty");
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
            int rowIndex = 0;
            for (Map<String, Object> row : rows) {
                boolean isEven = (rowIndex % 2 == 1);
                sb.append(isEven ? "<tr class='even report-row-even'>" : "<tr>");
                rowIndex++;
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
                        String valStr = formatGeneralValueWithEmptyFallback(c, v);
                        sb.append("<td>").append(valStr.isEmpty() ? "" : escHtml(valStr)).append("</td>");
                    }
                });
                sb.append("</tr>");
            }

            // ── Calculate and append Grand Total row for summary columns in Web UI HTML ──
            List<String> colList = new ArrayList<>(cols);
            double[] columnTotals = new double[colList.size()];
            boolean[] isNumericSummaryCol = new boolean[colList.size()];
            int firstNumericIdx = -1;

            for (int i = 0; i < colList.size(); i++) {
                if (isSummaryTotalColumn(colList.get(i))) {
                    isNumericSummaryCol[i] = true;
                    if (firstNumericIdx == -1) {
                        firstNumericIdx = i;
                    }
                }
            }

            boolean alreadyHasGrandTotalRow = isDatasetAlreadyTotaled(rows);

            if (firstNumericIdx != -1 && !alreadyHasGrandTotalRow) {
                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < colList.size(); i++) {
                        if (isNumericSummaryCol[i]) {
                            Object valObj = row.get(colList.get(i));
                            if (valObj != null) {
                                try {
                                    columnTotals[i] += Double.parseDouble(valObj.toString());
                                } catch (NumberFormatException e) {
                                    // ignore
                                }
                            }
                        }
                    }
                }

                int labelIdx = firstNumericIdx > 0 ? firstNumericIdx - 1 : 0;
                sb.append("<tr style='background: #f1f5f9; font-weight: bold; border-top: 2px solid #cbd5e1;'>");
                for (int k = 0; k < colList.size(); k++) {
                    if (k == labelIdx) {
                        sb.append("<td style='font-weight: bold; text-align: right;'>Grand Total</td>");
                    } else if (isNumericSummaryCol[k]) {
                        double tot = columnTotals[k];
                        String formattedTot = (tot == Math.floor(tot))
                            ? String.format(Locale.US, "%.0f", tot)
                            : String.format(Locale.US, "%.2f", tot);
                        sb.append("<td style='font-weight: bold;'>").append(formattedTot).append("</td>");
                    } else {
                        sb.append("<td></td>");
                    }
                }
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

        // PDF-specific CSS overrides: allow text wrapping, shrink font & padding so all columns fit on A4 landscape
        String pdfOverrides =
            "table{font-size:10px;table-layout:auto;width:100%;page-break-inside:auto}" +
            "th{white-space:normal;padding:5px 6px;font-size:10px}" +
            "td{white-space:normal;padding:4px 6px;font-size:10px;word-break:break-word}" +
            "body{font-size:10px}" +
            ".detail-table-title{page-break-after:avoid}" +
            "tbody tr:last-child{page-break-after:avoid}" +
            "thead{page-break-after:avoid}";

        // Override the hardcoded #525252 in REPORT_CSS (and any inline styles from
        // custom report builders) with the hospital's chosen theme colour.
        String themeHex = getThemeColor();
        String softHex = getSoftThemeColor(themeHex);
        String themeOverride =
            "thead tr{background:" + themeHex + " !important;color:#fff !important}" +
            "thead tr th{background:" + themeHex + " !important;color:#fff !important}" +
            "th{background:" + themeHex + " !important;color:#fff !important}" +
            ".report-header th{background:none !important;color:#1e293b !important}" +
            "button{background:" + themeHex + " !important;color:#fff !important}" +
            ".detail-table-title{color:" + themeHex + " !important}" +
            "tr.even td, tr.report-row-even td{background-color:" + softHex + " !important}" +
            "tr.even, tr.report-row-even{background-color:" + softHex + " !important}" +
            "tr:nth-child(even) td, tr:nth-child(even){background-color:" + softHex + " !important}" +
            ".summary{background-color:" + softHex + " !important;border-left:3px solid " + themeHex + " !important}";

        // Replace any hardcoded #525252 in the HTML content itself with the active theme color
        if (contentWithCriteria != null && themeHex != null && !themeHex.equalsIgnoreCase("#525252")) {
            contentWithCriteria = contentWithCriteria.replaceAll("(?i)#525252", themeHex);
        }
        if (contentWithCriteria != null && softHex != null) {
            contentWithCriteria = contentWithCriteria.replaceAll("(?i)#f8fafc", softHex);
        }

        // Apply alternate row colors based on selected theme for PDF rendering
        contentWithCriteria = applyAlternateRowStyles(contentWithCriteria, softHex);

        String fullHtml = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>" +
                          "@page { size: A4 landscape; margin-top: 28mm; margin-bottom: 15mm; margin-left: 10mm; margin-right: 10mm; @top-right { content: element(header); } }" +
                          REPORT_CSS +
                          pdfOverrides +
                          themeOverride +
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

        // Count number of <tbody> elements — if multiple, this is a multi-section
        // report (e.g. Net Collection Detail). Skip forced row-based pagination
        // for multi-section reports and let CSS page-break rules handle layout.
        int tbodyCount = 0;
        int searchFrom = 0;
        while (true) {
            int idx = html.indexOf("<tbody>", searchFrom);
            if (idx == -1) break;
            tbodyCount++;
            searchFrom = idx + 7;
        }
        if (tbodyCount != 1) {
            // Multi-section or no-table report — skip forced pagination
            return html;
        }

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

    public String applyAlternateRowStyles(String html, String softHex) {
        if (html == null || !html.contains("<tr")) return html;

        // Process tbody blocks if present
        java.util.regex.Pattern tbodyPattern = java.util.regex.Pattern.compile("(?is)<tbody([^>]*)>(.*?)</tbody>");
        java.util.regex.Matcher tbodyMatcher = tbodyPattern.matcher(html);
        if (tbodyMatcher.find()) {
            tbodyMatcher.reset();
            StringBuffer sb = new StringBuffer();
            while (tbodyMatcher.find()) {
                String attrs = tbodyMatcher.group(1);
                String body = tbodyMatcher.group(2);
                String styledBody = processRowsForAlternatingColor(body, softHex);
                tbodyMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("<tbody" + attrs + ">" + styledBody + "</tbody>"));
            }
            tbodyMatcher.appendTail(sb);
            return sb.toString();
        } else {
            // Process tables without explicit tbody
            java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile("(?is)<table([^>]*)>(.*?)</table>");
            java.util.regex.Matcher tableMatcher = tablePattern.matcher(html);
            StringBuffer sb = new StringBuffer();
            while (tableMatcher.find()) {
                String tableAttrs = tableMatcher.group(1);
                String tableContent = tableMatcher.group(2);
                if (tableAttrs.contains("report-header")) {
                    tableMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("<table" + tableAttrs + ">" + tableContent + "</table>"));
                    continue;
                }
                String styledTable = processRowsForAlternatingColor(tableContent, softHex);
                tableMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("<table" + tableAttrs + ">" + styledTable + "</table>"));
            }
            tableMatcher.appendTail(sb);
            return sb.toString();
        }
    }

    private String processRowsForAlternatingColor(String content, String softHex) {
        if (content == null || !content.contains("<tr")) return content;

        java.util.regex.Pattern rowPattern = java.util.regex.Pattern.compile("(?is)(<tr(\\s[^>]*)?>)(.*?)(</tr>)");
        java.util.regex.Matcher rowMatcher = rowPattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        int dataRowIndex = 0;

        while (rowMatcher.find()) {
            String fullTrTag = rowMatcher.group(1);
            String trAttrs = rowMatcher.group(2) != null ? rowMatcher.group(2) : "";
            String inner = rowMatcher.group(3);
            String closeTr = rowMatcher.group(4);

            // Skip header rows (contains <th)
            if (inner.toLowerCase().contains("<th")) {
                rowMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTrTag + inner + closeTr));
                continue;
            }

            // Skip department headers or full-width title rows (e.g. colspan with bold or title)
            if (trAttrs.toLowerCase().contains("detail-table-title") || inner.toLowerCase().contains("department :")) {
                dataRowIndex = 0; // reset sequence for new section
                rowMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTrTag + inner + closeTr));
                continue;
            }

            // Skip rows that already have explicit background (like total rows with border-top)
            boolean hasExplicitBg = trAttrs.toLowerCase().contains("background:") || trAttrs.toLowerCase().contains("background-color:");
            if (hasExplicitBg) {
                rowMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTrTag + inner + closeTr));
                continue;
            }

            // If row already marked as even/odd
            boolean alreadyEven = trAttrs.toLowerCase().contains("even");
            boolean alreadyOdd = trAttrs.toLowerCase().contains("odd");

            boolean isEven;
            if (alreadyEven) {
                isEven = true;
                dataRowIndex++;
            } else if (alreadyOdd) {
                isEven = false;
                dataRowIndex++;
            } else {
                // Determine by sequence (0-indexed: index 0 is row 1 [odd], index 1 is row 2 [even])
                isEven = (dataRowIndex % 2 == 1);
                dataRowIndex++;
            }

            if (isEven) {
                String updatedAttrs = trAttrs;
                if (updatedAttrs.contains("class='")) {
                    updatedAttrs = updatedAttrs.replace("class='", "class='even report-row-even ");
                } else if (updatedAttrs.contains("class=\"")) {
                    updatedAttrs = updatedAttrs.replace("class=\"", "class=\"even report-row-even ");
                } else if (!updatedAttrs.contains("class=")) {
                    updatedAttrs = " class='even report-row-even'" + updatedAttrs;
                }

                if (updatedAttrs.contains("style='")) {
                    updatedAttrs = updatedAttrs.replace("style='", "style='background-color: " + softHex + "; ");
                } else if (updatedAttrs.contains("style=\"")) {
                    updatedAttrs = updatedAttrs.replace("style=\"", "style=\"background-color: " + softHex + "; ");
                } else {
                    updatedAttrs = " style='background-color: " + softHex + ";'" + updatedAttrs;
                }

                String newTrTag = "<tr" + updatedAttrs + ">";
                rowMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(newTrTag + inner + closeTr));
            } else {
                rowMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(fullTrTag + inner + closeTr));
            }
        }
        rowMatcher.appendTail(sb);
        return sb.toString();
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
        cols.remove("patient_id");
        cols.remove("encounter_id");
        cols.remove("supplier_id");
        cols.remove("user_id");
        cols.remove("item_id");
        cols.remove("po_notes");
        cols.remove("__EMPTY_ROW__");
        cols.remove("Chargeable Qty");
        cols.remove("Free Qty");

        // ── Merge Age + Sex/Gender into a single "Age/Sex" column ──
        String ageKey    = cols.contains("Age") ? "Age" : cols.contains("age") ? "age" : null;
        String sexKey    = cols.contains("Sex") ? "Sex" : cols.contains("sex") ? "sex" : cols.contains("Gender") ? "Gender" : cols.contains("gender") ? "gender" : null;
        boolean mergeAgeSex = ageKey != null && sexKey != null;
        if (mergeAgeSex) {
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

        List<String> humanisedCols = cols.stream().map(this::humanise).toList();
        sb.append(String.join(",", humanisedCols)).append("\n");
        
        if (!isEmptyRow) {
            List<String> colList = new ArrayList<>(cols);
            double[] columnTotals = new double[colList.size()];
            boolean[] isNumericSummaryCol = new boolean[colList.size()];
            int firstNumericIdx = -1;

            for (int i = 0; i < colList.size(); i++) {
                if (isSummaryTotalColumn(colList.get(i))) {
                    isNumericSummaryCol[i] = true;
                    if (firstNumericIdx == -1) firstNumericIdx = i;
                }
            }

            for (Map<String, Object> row : rows) {
                StringJoiner sj = new StringJoiner(",");
                for (int i = 0; i < colList.size(); i++) {
                    String c = colList.get(i);
                    Object v = row.get(c);
                    String valStr;
                    if (finalMerge && "Age/Sex".equals(c)) {
                        String age = formatGeneralValue(row.get(finalAgeKey));
                        age = age.replaceAll("\\s*Y$", "").trim();
                        String ageVal = age.isEmpty() ? "-" : age;
                        String sexFull = formatGeneralValue(row.get(finalSexKey)).toUpperCase();
                        String sex = sexFull.isEmpty() ? "-" :
                            sexFull.startsWith("M") ? "M" :
                            sexFull.startsWith("F") ? "F" :
                            sexFull.startsWith("T") ? "T" : "-";
                        valStr = ageVal + "/" + sex;
                    } else {
                        valStr = formatGeneralValueWithEmptyFallback(c, v);
                    }
                    if (v instanceof Number) {
                        double dVal = ((Number) v).doubleValue();
                        if (isNumericSummaryCol[i]) columnTotals[i] += dVal;
                    } else if (v != null && isNumericSummaryCol[i]) {
                        try {
                            double dVal = Double.parseDouble(v.toString());
                            columnTotals[i] += dVal;
                        } catch (NumberFormatException e) { }
                    }
                    String s = valStr.replace("\"", "\"\"");
                    sj.add("\"" + s + "\"");
                }
                sb.append(sj).append("\n");
            }

            boolean alreadyHasGrandTotalRow = isDatasetAlreadyTotaled(rows);

            if (firstNumericIdx != -1 && !alreadyHasGrandTotalRow) {
                StringJoiner totalSj = new StringJoiner(",");
                int labelIdx = firstNumericIdx > 0 ? firstNumericIdx - 1 : 0;
                for (int i = 0; i < colList.size(); i++) {
                    if (i == labelIdx) {
                        totalSj.add("\"Grand Total\"");
                    } else if (isNumericSummaryCol[i]) {
                        double tot = columnTotals[i];
                        if (tot == Math.floor(tot)) {
                            totalSj.add(String.format(java.util.Locale.US, "\"%.0f\"", tot));
                        } else {
                            totalSj.add(String.format(java.util.Locale.US, "\"%.2f\"", tot));
                        }
                    } else {
                        totalSj.add("\"\"");
                    }
                }
                sb.append(totalSj).append("\n");
            }
        }
        return sb.toString();
    }

    /** Backward-compatible overload (no header) */
    public String buildCsv(List<Map<String, Object>> rows) {
        return buildCsv(rows, null, null);
    }

    /**
     * Builds a proper XLSX workbook with bold header row, auto-sized columns,
     * and a styled hospital/report title section at the top.
     */
    public byte[] buildXlsx(List<Map<String, Object>> rows, String reportDescription, Map<String, Object> params) {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String rawSheetName = (reportDescription != null && !reportDescription.isEmpty())
                    ? reportDescription
                    : "Report";
            String safeSheetName = org.apache.poi.ss.util.WorkbookUtil.createSafeSheetName(rawSheetName);
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet(safeSheetName);

            // ── Style: Bold for titles and headers ──
            org.apache.poi.xssf.usermodel.XSSFCellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            org.apache.poi.xssf.usermodel.XSSFCellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            String themeHex = getThemeColor();
            if (themeHex != null && themeHex.matches("^#[0-9a-fA-F]{6}$")) {
                int r = Integer.parseInt(themeHex.substring(1, 3), 16);
                int g = Integer.parseInt(themeHex.substring(3, 5), 16);
                int b = Integer.parseInt(themeHex.substring(5, 7), 16);
                org.apache.poi.xssf.usermodel.XSSFColor themeColor = new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
                headerStyle.setFillForegroundColor(themeColor);
            } else {
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
            }
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            org.apache.poi.xssf.usermodel.XSSFCellStyle subTitleStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont subTitleFont = workbook.createFont();
            subTitleFont.setBold(true);
            subTitleFont.setFontHeightInPoints((short) 11);
            subTitleStyle.setFont(subTitleFont);

            org.apache.poi.xssf.usermodel.XSSFCellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);

            int rowIdx = 0;
            List<Integer> titleRowsToMerge = new ArrayList<>();

            // ── Hospital name ──
            Map<String, String> hospitalParams2 = settingsRegistry.getValueMapByType("HOSPITAL_PARAM");
            String hospitalName = hospitalParams2.getOrDefault("hospital.name.param", "HMS Hospital");
            org.apache.poi.xssf.usermodel.XSSFRow hospitalRow = sheet.createRow(rowIdx++);
            org.apache.poi.xssf.usermodel.XSSFCell hospitalCell = hospitalRow.createCell(0);
            hospitalCell.setCellValue(hospitalName);
            hospitalCell.setCellStyle(titleStyle);
            titleRowsToMerge.add(hospitalRow.getRowNum());

            // ── Hospital address ──
            String hospitalAddress = hospitalParams2.getOrDefault("hospital.address.param", "");
            if (!hospitalAddress.isEmpty()) {
                org.apache.poi.xssf.usermodel.XSSFRow addrRow = sheet.createRow(rowIdx++);
                addrRow.createCell(0).setCellValue(hospitalAddress);
                titleRowsToMerge.add(addrRow.getRowNum());
            }

            rowIdx++; // blank row

            // ── Report title ──
            if (reportDescription != null && !reportDescription.isEmpty()) {
                org.apache.poi.xssf.usermodel.XSSFRow descRow = sheet.createRow(rowIdx++);
                org.apache.poi.xssf.usermodel.XSSFCell descCell = descRow.createCell(0);
                descCell.setCellValue(reportDescription);
                descCell.setCellStyle(subTitleStyle);
                titleRowsToMerge.add(descRow.getRowNum());
            }

            // ── Date range / search criteria ──
            String criteria = formatSearchCriteria(params);
            if (!criteria.isEmpty()) {
                org.apache.poi.xssf.usermodel.XSSFRow critRow = sheet.createRow(rowIdx++);
                critRow.createCell(0).setCellValue(criteria);
                titleRowsToMerge.add(critRow.getRowNum());
            }

            rowIdx++; // blank row before data

            if (rows.isEmpty()) {
                workbook.write(out);
                return out.toByteArray();
            }

            boolean isEmptyRow = rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__"));

            Set<String> cols = new java.util.LinkedHashSet<>(rows.get(0).keySet());
            cols.remove("consultant_id");
            cols.remove("department_id");
            cols.remove("patient_id");
            cols.remove("encounter_id");
            cols.remove("supplier_id");
            cols.remove("user_id");
            cols.remove("item_id");
            cols.remove("po_notes");
            cols.remove("__EMPTY_ROW__");
            cols.remove("Chargeable Qty");
            cols.remove("Free Qty");

            // ── Merge Age + Sex/Gender into a single "Age/Sex" column ──
            String ageKey    = cols.contains("Age") ? "Age" : cols.contains("age") ? "age" : null;
            String sexKey    = cols.contains("Sex") ? "Sex" : cols.contains("sex") ? "sex" : cols.contains("Gender") ? "Gender" : cols.contains("gender") ? "gender" : null;
            boolean mergeAgeSex = ageKey != null && sexKey != null;
            if (mergeAgeSex) {
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

            List<String> colList = new ArrayList<>(cols);
            List<String> humanisedCols = colList.stream().map(this::humanise).toList();

            // ── Merge title rows across data columns so they do not stretch column 0 ──
            if (humanisedCols.size() > 1) {
                int lastColIdx = humanisedCols.size() - 1;
                for (int rNum : titleRowsToMerge) {
                    sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rNum, rNum, 0, lastColIdx));
                }
            }

            // ── Column header row (BOLD) ──
            org.apache.poi.xssf.usermodel.XSSFRow headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < humanisedCols.size(); i++) {
                org.apache.poi.xssf.usermodel.XSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(humanisedCols.get(i));
                cell.setCellStyle(headerStyle);
            }

            // ── Data rows ──
            if (!isEmptyRow) {
                double[] columnTotals = new double[colList.size()];
                boolean[] isNumericSummaryCol = new boolean[colList.size()];
                int firstNumericIdx = -1;

                for (int i = 0; i < colList.size(); i++) {
                    if (isSummaryTotalColumn(colList.get(i))) {
                        isNumericSummaryCol[i] = true;
                        if (firstNumericIdx == -1) {
                            firstNumericIdx = i;
                        }
                    }
                }

                boolean alreadyHasGrandTotalRow = isDatasetAlreadyTotaled(rows);

                org.apache.poi.xssf.usermodel.XSSFCellStyle totalStyle = workbook.createCellStyle();
                org.apache.poi.xssf.usermodel.XSSFFont totalFont = workbook.createFont();
                totalFont.setBold(true);
                totalStyle.setFont(totalFont);
                totalStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
                totalStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                totalStyle.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);

                for (Map<String, Object> row : rows) {
                    boolean isDbGrandTotalRow = isTotalRow(row);

                    org.apache.poi.xssf.usermodel.XSSFRow dataRow = sheet.createRow(rowIdx++);
                    for (int i = 0; i < colList.size(); i++) {
                        String colKey = colList.get(i);
                        org.apache.poi.xssf.usermodel.XSSFCell cell = dataRow.createCell(i);
                        if (isDbGrandTotalRow) {
                            cell.setCellStyle(totalStyle);
                        }
                        
                        boolean isNarrow = isNarrowColumn(colKey);

                        if (finalMerge && "Age/Sex".equals(colKey)) {
                            String age = formatGeneralValue(row.get(finalAgeKey));
                            age = age.replaceAll("\\s*Y$", "").trim();
                            String ageVal = age.isEmpty() ? "-" : age;
                            String sexFull = formatGeneralValue(row.get(finalSexKey)).toUpperCase();
                            String sex = sexFull.isEmpty() ? "-" :
                                sexFull.startsWith("M") ? "M" :
                                sexFull.startsWith("F") ? "F" :
                                sexFull.startsWith("T") ? "T" : "-";
                            cell.setCellValue(ageVal + "/" + sex);
                            if (isNarrow && !isDbGrandTotalRow) {
                                cell.setCellStyle(centerStyle);
                            }
                        } else {
                            Object v = row.get(colKey);
                            String valStr = formatGeneralValueWithEmptyFallback(colKey, v);
                            if (v instanceof Number) {
                                double dVal = ((Number) v).doubleValue();
                                cell.setCellValue(dVal);
                                if (isNumericSummaryCol[i]) {
                                    columnTotals[i] += dVal;
                                }
                            } else if (v != null && isNumericSummaryCol[i]) {
                                try {
                                    double dVal = Double.parseDouble(v.toString());
                                    cell.setCellValue(dVal);
                                    columnTotals[i] += dVal;
                                } catch (NumberFormatException e) {
                                    cell.setCellValue(valStr);
                                }
                            } else {
                                cell.setCellValue(valStr);
                            }
                            if (isNarrow && !isDbGrandTotalRow) {
                                cell.setCellStyle(centerStyle);
                            }
                        }
                    }
                }

                // ── Grand Total row (only append if not already present in dataset) ──
                if (firstNumericIdx != -1 && !alreadyHasGrandTotalRow) {
                    org.apache.poi.xssf.usermodel.XSSFRow totalRow = sheet.createRow(rowIdx++);

                    int labelIdx = firstNumericIdx > 0 ? firstNumericIdx - 1 : 0;
                    for (int i = 0; i < colList.size(); i++) {
                        org.apache.poi.xssf.usermodel.XSSFCell cell = totalRow.createCell(i);
                        cell.setCellStyle(totalStyle);
                        if (i == labelIdx) {
                            cell.setCellValue("Grand Total");
                        } else if (isNumericSummaryCol[i]) {
                            cell.setCellValue(columnTotals[i]);
                        }
                    }
                }
            }

            // ── Auto-size columns ──
            for (int i = 0; i < humanisedCols.size(); i++) {
                try {
                    String colHeader = humanisedCols.get(i);
                    boolean isNarrow = isNarrowColumn(colHeader);
                    if (isNarrow) {
                        sheet.setColumnWidth(i, 2048); // ~8 character width, neat and compact for S.No
                    } else {
                        sheet.autoSizeColumn(i, false);
                        int currentWidth = sheet.getColumnWidth(i);
                        sheet.setColumnWidth(i, Math.max(Math.min(currentWidth + 768, 16000), 3072));
                    }
                } catch (Exception e) {
                    sheet.setColumnWidth(i, 4000);
                }
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("Failed to generate XLSX: {}", ex.getMessage());
            throw new com.hms.exception.BusinessRuleViolationException("XLSX generation failed: " + ex.getMessage());
        }
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
        Map<String, String> hospitalParams = settingsRegistry != null ? settingsRegistry.getValueMapByType("HOSPITAL_PARAM") : Collections.emptyMap();
        String hospitalName = escHtml(hospitalParams.getOrDefault("hospital.name.param", "HMS Hospital"));
        String hospitalAddress = escHtml(hospitalParams.getOrDefault("hospital.address.param", ""));
        String hospitalContact = escHtml(hospitalParams.getOrDefault("hospital.contactNo.param", ""));

        // Try to load hospital logo as base64
        String logoImgTag = "";
        try {
            Optional<Attachment> logoOpt = attachmentService != null ? attachmentService.getLatestByCategory("HOSPITAL_LOGO") : Optional.empty();
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
                            value = decryptFormatted(name);
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

    public String getHospitalName() {
        Map<String, String> hp = settingsRegistry.getValueMapByType("HOSPITAL_PARAM");
        return hp.getOrDefault("hospital.name.param", "HMS Hospital");
    }

    public String getHospitalAddress() {
        Map<String, String> hp = settingsRegistry.getValueMapByType("HOSPITAL_PARAM");
        return hp.getOrDefault("hospital.address.param", "");
    }

    public boolean isDatasetAlreadyTotaled(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return false;
        Map<String, Object> lastRow = rows.get(rows.size() - 1);
        for (Object val : lastRow.values()) {
            if (val != null) {
                String s = val.toString().trim().toLowerCase();
                if (s.equals("total") || s.equals("grand total") || s.equals("overall total") ||
                    s.equals("total:") || s.equals("grand total:") || s.equals("overall total:")) {
                    return true;
                }
            }
        }
        for (Map<String, Object> r : rows) {
            for (Object val : r.values()) {
                if (val != null) {
                    String s = val.toString().trim().toLowerCase();
                    if (s.equals("grand total") || s.equals("overall total") || s.equals("grand total:") || s.equals("overall total:")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isTotalRow(Map<String, Object> row) {
        if (row == null) return false;
        for (Object obj : row.values()) {
            if (obj != null) {
                String s = obj.toString().trim().toLowerCase();
                if (s.equals("total") || s.equals("grand total") || s.equals("overall total") ||
                    s.equals("total:") || s.equals("grand total:") || s.equals("overall total:")) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isSummaryTotalColumn(String colKey) {
        if (colKey == null) return false;
        String key = colKey.trim().toLowerCase();

        // 1. Explicit exclusions: Identifiers, dates, text fields, rates, age/sex
        if (key.contains("date") ||
            key.endsWith("_no") || key.endsWith(" no") || key.equals("no") || key.equals("s.no") || key.equals("s.no.") || key.equals("s_no") ||
            key.contains("number") || key.contains("code") || key.contains("id") || key.contains("mode") ||
            key.contains("status") || key.contains("user") || key.contains("by") || key.contains("reason") ||
            key.contains("remark") || key.contains("supplier") || key.contains("manufacturer") ||
            key.contains("specimen") || key.contains("department") || key.contains("ward") || key.contains("bed") ||
            key.contains("patient name") || key.contains("consultant name") || key.equals("patient") || key.equals("consultant") ||
            key.equals("age") || key.equals("sex") || key.equals("gender") || key.equals("age/sex") ||
            key.equals("mrp") || key.equals("unit_rate") || key.equals("unit_price") || key.equals("purchase_price") ||
            key.equals("rate") || key.equals("price") || key.equals("given_to") || key.equals("paid to")) {
            return false;
        }

        // 2. Explicit inclusions: Amounts, quantities, values, counts, totals, balances
        if (key.contains("amount") || key.contains("qty") || key.contains("value") || key.equals("val") ||
            key.contains("deposit") || key.contains("refund") || key.contains("discount") || key.equals("net") ||
            key.contains("net_") || key.contains("net ") || key.contains("cash") || key.contains("card") || key.contains("upi") ||
            key.contains("total") || key.contains("fee") || key.equals("paid") || key.equals("due") || key.contains("balance") ||
            key.contains("patients") || key.equals("male") || key.equals("female") || key.equals("encounter") || key.equals("consulted")) {
            return true;
        }

        return false;
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
        String str = v.toString();
        return decryptFormatted(str);
    }

    public String formatGeneralValueWithEmptyFallback(String columnName, Object value) {
        String valStr = formatGeneralValue(value);
        if (valStr.isEmpty() && columnName != null) {
            String lower = columnName.toLowerCase();
            if (lower.equals("po_no") || lower.equals("po_number") || lower.equals("pono")) {
                return "Direct Purchase";
            }
            if (lower.equals("patient_number") ||
                lower.equals("patient_no") ||
                lower.equals("patientno") ||
                lower.equals("patientnumber") ||
                lower.equals("prescribed_by") ||
                lower.equals("prescribedby") ||
                lower.equals("prescribed_by_name") ||
                lower.equals("prescribed by")) {
                return "-";
            }
        }
        return valStr;
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

    public String decryptFormatted(String val) {
        if (val == null || val.isBlank()) return val;
        // 1. Try the entire value as a single encrypted token
        if (piiEncryptionService.looksEncrypted(val)) {
            try { return piiEncryptionService.decrypt(val); } catch (Exception ignored) {}
        }
        // 2. Split by whitespace and attempt per-token decryption,
        //    stripping trailing punctuation that SQL concatenation may have
        //    appended (e.g. ", MBBS" after encrypted last_name → "ciphertext,")
        String[] parts = val.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(" ");
            String part = parts[i];
            // Strip trailing punctuation before attempting decryption
            int end = part.length();
            while (end > 0 && ",.:;)]".indexOf(part.charAt(end - 1)) >= 0) {
                end--;
            }
            if (end > 0 && end < part.length()) {
                String core = part.substring(0, end);
                String tail = part.substring(end);
                if (piiEncryptionService.looksEncrypted(core)) {
                    try {
                        sb.append(piiEncryptionService.decrypt(core)).append(tail);
                        continue;
                    } catch (Exception ignored) {}
                }
            }
            // Try the token as-is (no trailing punctuation case)
            if (piiEncryptionService.looksEncrypted(part)) {
                try {
                    sb.append(piiEncryptionService.decrypt(part));
                    continue;
                } catch (Exception ignored) {}
            }
            sb.append(part);
        }
        return sb.toString().trim();
    }
}
