package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CollectionReportService extends BaseReportService {

    private final CollectionReportDataService ds;

    public CollectionReportService(ReportEngine reportEngine, CollectionReportDataService ds) {
        super(reportEngine);
        this.ds = ds;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "net_collection_summary", "description", "Net Collection Summary", "category", "Collections"),
        Map.of("name", "net_collection_detail",  "description", "Net Collection Report",  "category", "Collections"),
        Map.of("name", "receipts_summary",       "description", "Receipts Summary",       "category", "Collections"),
        Map.of("name", "receipts_detail",        "description", "Receipt Detail Report",  "category", "Collections"),
        Map.of("name", "deposits_summary",       "description", "Deposits Summary",       "category", "Collections"),
        Map.of("name", "deposits_detail",        "description", "Deposit Detail Report",  "category", "Collections"),
        Map.of("name", "refunds_summary",        "description", "Refunds Summary",        "category", "Collections"),
        Map.of("name", "refunds_detail",         "description", "Refund Detail Report",   "category", "Collections"),
        Map.of("name", "petty_cash_summary",     "description", "Petty Cash Summary",     "category", "Collections"),
        Map.of("name", "petty_cash_detail",      "description", "Petty Cash Detail Report","category", "Collections")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;
    private static final List<Map<String, Object>> DATE_VISIT_PARAMS = List.of(
        param("from_date", "DATE", true, "", "From Date"),
        param("to_date", "DATE", true, "", "To Date"),
        param("visit", "VISIT", false, "ALL", "Visit")
    );
    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        for (Map<String, String> r : CATALOGUE) {
            if ("refunds_detail".equals(r.get("name"))) {
                m.put(r.get("name"), DATE_VISIT_PARAMS);
            } else {
                m.put(r.get("name"), DATE_RANGE_PARAMS);
            }
        }
        PARAMS = Collections.unmodifiableMap(m);
    }

    @Override
    public List<Map<String, String>> getAvailableReports() { return CATALOGUE; }

    @Override
    public Map<String, Object> getReportInfo(String reportName) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("reportName", reportName);
        CATALOGUE.stream().filter(r -> r.get("name").equals(reportName)).findFirst()
            .ifPresent(meta -> { info.put("description", meta.get("description")); info.put("category", meta.get("category")); });
        info.put("parameters", PARAMS.getOrDefault(reportName, List.of()));
        return info;
    }

    public List<Map<String, Object>> executeDataQuery(String reportName, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        String visit = reportEngine.str(params, "visit");
        if (visit.isEmpty()) visit = "ALL";
        return switch (reportName) {
            case "net_collection_summary", "net_collection_detail" -> ds.getNetCollectionSummary(from, to);
            case "receipts_summary"  -> ds.getReceiptsSummary(from, to);
            case "receipts_detail"   -> ds.getReceiptsDetail(from, to);
            case "deposits_summary"  -> ds.getDepositsSummary(from, to);
            case "deposits_detail"   -> ds.getDepositsDetail(from, to);
            case "refunds_summary"   -> ds.getRefundsSummary(from, to);
            case "refunds_detail"    -> ds.getRefundsDetail(from, to, visit);
            case "petty_cash_summary"-> ds.getPettyCashSummary(from, to);
            case "petty_cash_detail" -> ds.getPettyCashDetail(from, to);
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if ("net_collection_detail".equals(reportName)) {
            return buildNetCollectionDetailHtml(rows, params);
        }
        if ("receipts_detail".equals(reportName)) {
            return buildReceiptsDetailHtml(rows, params);
        }
        if ("deposits_detail".equals(reportName)) {
            return buildDepositsDetailHtml(rows, params);
        }
        if ("refunds_detail".equals(reportName)) {
            return buildRefundsDetailHtml(rows, params);
        }
        if ("petty_cash_detail".equals(reportName)) {
            return buildPettyCashDetailHtml(rows, params);
        }
        return null;
    }

    // ── Net Collection Detail (multi-section) ─────────────────────────────
    private String buildNetCollectionDetailHtml(List<Map<String, Object>> summaryRows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        String fromFmt = fmtDate(from); String toFmt = fmtDate(to);

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='margin-bottom:20px;'>");
        sb.append("<div style='font-size:12px;color:#64748b;'>Net Collection from ").append(fromFmt).append(" to ").append(toFmt).append("</div>");
        sb.append("</div>");

        // ── Section 1: Collection Summary ──
        sb.append("<h3 style='font-size:14px;font-weight:bold;margin:16px 0 8px 0;'>Collection Summary</h3>");
        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;' rowspan='2'>User</th>");
        sb.append("<th style='padding:8px 10px;text-align:center;' colspan='3'>Collection</th>");
        sb.append("<th style='padding:8px 10px;text-align:center;' rowspan='2'>Petty Cash</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;' rowspan='2'>Net</th>");
        sb.append("</tr><tr style='background:#1e40af;color:#fff;'>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#1e40af;color:#fff;'>Cash</th>");

        sb.append("<th style='padding:6px 10px;text-align:right;background:#1e40af;color:#fff;'>Card</th>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#1e40af;color:#fff;'>Fund Transfer</th>");
        sb.append("</tr></thead><tbody>");

        double tCash=0, tCard=0, tFund=0, tPetty=0, tNet=0;
        for (Map<String, Object> r : summaryRows) {
            double cash = reportEngine.doubleVal(r.get("collection_cash"));

            double card = reportEngine.doubleVal(r.get("card"));
            double fund = reportEngine.doubleVal(r.get("fund_transfer"));
            double petty = reportEngine.doubleVal(r.get("petty_cash"));
            double net = reportEngine.doubleVal(r.get("net"));
            tCash+=cash; tCard+=card; tFund+=fund; tPetty+=petty; tNet+=net;

            sb.append("<tr>");
            td(sb, reportEngine.str(r, "user"), "left");
            tdN(sb, cash); tdN(sb, card); tdN(sb, fund); tdN(sb, petty); tdN(sb, net);
            sb.append("</tr>");
        }
        sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
        td(sb, "Total", "left");
        tdN(sb, tCash); tdN(sb, tCard); tdN(sb, tFund); tdN(sb, tPetty); tdN(sb, tNet);
        sb.append("</tr></tbody></table>");

        // ── Section 2: Receipt Detail ──
        List<Map<String, Object>> receipts = ds.getReceiptsDetail(from, to);
        sb.append("<h3 style='font-size:14px;font-weight:bold;margin:20px 0 8px 0;'>Collection Detail</h3>");
        sb.append("<div style='font-size:12px;font-weight:bold;margin:0 0 6px 10px;'>Receipts</div>");
        buildDetailTable(sb, receipts, new String[]{"receipt_no","rcpt_date","bill_no","bill_date","patient_no","patient","mode","payment_details","amount","user"},
                new String[]{"Receipt No","Rcpt Date","Bill No","Bill Date","Patient No","Patient","Mode","Payment Details","Amount (Rs)","User"});

        // ── Section 3: Deposit Detail ──
        List<Map<String, Object>> deposits = ds.getDepositsDetail(from, to);
        sb.append("<div style='font-size:12px;font-weight:bold;margin:16px 0 6px 10px;'>Deposits</div>");
        buildDetailTable(sb, deposits, new String[]{"deposit_no","dpst_date","patient_no","patient","deposit","adj_against_bill","bill_date","adj_amnt","balance"},
                new String[]{"Deposit No","Dpst Date","Patient No","Patient","Deposit","Adj against Bill","Bill Date","Adj Amnt","Balance"});

        // ── Section 4: Refund Detail ──
        List<Map<String, Object>> refunds = ds.getRefundsDetail(from, to, "ALL");
        sb.append("<div style='font-size:12px;font-weight:bold;margin:16px 0 6px 10px;'>Refunds</div>");
        buildDetailTable(sb, refunds, new String[]{"refund_no","refund_date","bill_no","bill_date","patient_no","patient","mode","amount","user","reason"},
                new String[]{"Refund No","Refund Date","Bill No","Bill Date","Patient No","Patient","Mode","Amount (Rs)","User","Reason"});

        sb.append("</div>");
        return sb.toString();
    }

    private void buildDetailTable(StringBuilder sb, List<Map<String, Object>> rows, String[] keys, String[] headers) {
        sb.append("<table><thead><tr>");
        for (String h : headers) sb.append("<th style='padding:8px 10px;text-align:left;'>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='").append(headers.length).append("' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                for (String k : keys) {
                    Object v = r.get(k);
                    String val = (v instanceof java.sql.Date || v instanceof java.time.LocalDate)
                            ? reportEngine.formatDateValue(v) : reportEngine.formatGeneralValue(v);
                    sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(val)).append("</td>");
                }
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table>");
    }

    // ── Receipts Detail (standalone view) ─────────────────────────────────
    private String buildReceiptsDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='font-size:12px;color:#64748b;margin-bottom:12px;'>Receipts from ").append(fmtDate(from)).append(" to ").append(fmtDate(to)).append("</div>");
        buildDetailTable(sb, rows, new String[]{"receipt_no","rcpt_date","bill_no","bill_date","patient_no","patient","mode","payment_details","amount","user"},
                new String[]{"Receipt No","Rcpt Date","Bill No","Bill Date","Patient No","Patient","Mode","Payment Details","Amount (Rs)","User"});
        sb.append("</div>");
        return sb.toString();
    }

    // ── Deposits Detail (standalone view) ─────────────────────────────────
    private String buildDepositsDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='font-size:12px;color:#64748b;margin-bottom:12px;'>Deposits from ").append(fmtDate(from)).append(" to ").append(fmtDate(to)).append("</div>");
        buildDetailTable(sb, rows, new String[]{"deposit_no","dpst_date","patient_no","patient","deposit","adj_against_bill","bill_date","adj_amnt","balance"},
                new String[]{"Deposit No","Dpst Date","Patient No","Patient","Deposit","Adj against Bill","Bill Date","Adj Amnt","Balance"});
        sb.append("</div>");
        return sb.toString();
    }

    // ── Refunds Detail (standalone view) ──────────────────────────────────
    private String buildRefundsDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='font-size:12px;color:#64748b;margin-bottom:12px;'>Refunds from ").append(fmtDate(from)).append(" to ").append(fmtDate(to)).append("</div>");
        
        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;'>S.No</th>");
        String[] headers = {"Refund No","Refund Date","Bill No","Bill Date","Patient No","Patient Name","Mode","Refund Reason","Amount (Rs)","User"};
        for(String h: headers) sb.append("<th style='padding:8px 10px;text-align:left;'>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='11' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            int sno = 1;
            double totalAmount = 0;
            String[] keys = {"refund_no","refund_date","bill_no","bill_date","patient_no","patient_name","mode","refund_reason","amount","user"};
            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                td(sb, String.valueOf(sno++), "left");
                for (String k : keys) {
                    Object v = r.get(k);
                    if ("amount".equals(k)) {
                        totalAmount += reportEngine.doubleVal(v);
                        tdN(sb, reportEngine.doubleVal(v));
                    } else {
                        String val = (v instanceof java.sql.Date || v instanceof java.time.LocalDate)
                            ? reportEngine.formatDateValue(v) : reportEngine.formatGeneralValue(v);
                        td(sb, val, "left");
                    }
                }
                sb.append("</tr>");
            }
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td colspan='9' style='text-align:right;padding:6px 10px;'>Total : Rs.</td>");
            tdN(sb, totalAmount);
            sb.append("<td></td></tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // ── Petty Cash Detail (standalone view) ───────────────────────────────
    private String buildPettyCashDetailHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='font-size:12px;color:#64748b;margin-bottom:12px;'>Petty Cash from ").append(fmtDate(from)).append(" to ").append(fmtDate(to)).append("</div>");
        
        sb.append("<table><thead><tr>");
        String[] headers = {"Petty Cash No","Date","Paid To","Mode","Remark","Amount (Rs)","User"};
        for(String h: headers) sb.append("<th style='padding:8px 10px;text-align:left;'>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='7' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            double totalAmount = 0;
            String[] keys = {"petty_cash_no","date","paid_to","mode","remark","amount","user"};
            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                for (String k : keys) {
                    Object v = r.get(k);
                    if ("amount".equals(k)) {
                        totalAmount += reportEngine.doubleVal(v);
                        tdN(sb, reportEngine.doubleVal(v));
                    } else {
                        String val = (v instanceof java.sql.Date || v instanceof java.time.LocalDate)
                            ? reportEngine.formatDateValue(v) : reportEngine.formatGeneralValue(v);
                        td(sb, val, "left");
                    }
                }
                sb.append("</tr>");
            }
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td colspan='5' style='text-align:right;padding:6px 10px;'>Total : Rs.</td>");
            tdN(sb, totalAmount);
            sb.append("<td></td></tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void td(StringBuilder sb, String val, String align) {
        sb.append("<td style='padding:6px 10px;text-align:").append(align).append(";'>").append(reportEngine.escHtml(val)).append("</td>");
    }
    private void tdN(StringBuilder sb, double val) {
        sb.append("<td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(val)).append("</td>");
    }
    private String fmtDate(String iso) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(iso);
            return d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception e) { return iso; }
    }
}
