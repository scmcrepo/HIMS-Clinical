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
    private static final List<Map<String, Object>> DATE_USER_PARAMS = List.of(
        param("from_date", "DATE", true, "", "From Date"),
        param("to_date", "DATE", true, "", "To Date"),
        param("user", "USER", false, "ALL", "User")
    );
    private static final List<Map<String, Object>> DATE_VISIT_USER_PARAMS = List.of(
        param("from_date", "DATE", true, "", "From Date"),
        param("to_date", "DATE", true, "", "To Date"),
        param("visit", "VISIT", false, "ALL", "Encounter"),
        param("user", "USER", false, "ALL", "User"),
        param("mode", "PAYMENT_MODE", false, "ALL", "Payment Mode")
    );
    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        for (Map<String, String> r : CATALOGUE) {
            String name = r.get("name");
            if ("receipts_detail".equals(name) || "deposits_detail".equals(name) || "refunds_detail".equals(name)) {
                m.put(name, DATE_VISIT_USER_PARAMS);
            } else {
                m.put(name, DATE_RANGE_PARAMS);
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
        String user = reportEngine.str(params, "user");
        if (user.isEmpty()) user = "ALL";
        String mode = reportEngine.str(params, "mode");
        if (mode.isEmpty()) mode = "ALL";
        return switch (reportName) {
            case "net_collection_summary", "net_collection_detail" -> ds.getNetCollectionSummary(from, to);
            case "receipts_summary"  -> ds.getReceiptsSummary(from, to);
            case "receipts_detail"   -> ds.getReceiptsDetail(from, to, visit, user, mode);
            case "deposits_summary"  -> ds.getDepositsSummary(from, to);
            case "deposits_detail"   -> ds.getDepositsDetail(from, to, visit, user, mode);
            case "refunds_summary"   -> ds.getRefundsSummary(from, to);
            case "refunds_detail"    -> ds.getRefundsDetail(from, to, visit, user, mode);
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

        List<Map<String, Object>> receipts = ds.getReceiptsDetail(from, to, "ALL", "ALL", "ALL");
        List<Map<String, Object>> deposits = ds.getDepositsDetail(from, to, "ALL", "ALL", "ALL");
        List<Map<String, Object>> refunds = ds.getRefundsDetail(from, to, "ALL", "ALL", "ALL");
        List<Map<String, Object>> discounts = ds.getDiscountsDetail(from, to);

        // Find all unique usernames across all collections/payments/refunds/discounts
        Set<String> usernames = new LinkedHashSet<>();
        for (Map<String, Object> r : summaryRows) {
            String u = reportEngine.str(r, "user");
            if (!u.isEmpty()) usernames.add(u);
        }
        for (Map<String, Object> r : receipts) {
            String u = reportEngine.str(r, "user");
            if (!u.isEmpty()) usernames.add(u);
        }
        for (Map<String, Object> r : deposits) {
            String u = reportEngine.str(r, "user");
            if (!u.isEmpty()) usernames.add(u);
        }
        for (Map<String, Object> r : refunds) {
            String u = reportEngine.str(r, "user");
            if (!u.isEmpty()) usernames.add(u);
        }
        for (Map<String, Object> r : discounts) {
            String u = reportEngine.str(r, "user");
            if (!u.isEmpty()) usernames.add(u);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");

        // ── Main Summary View Container ──
        sb.append("<div id='summary-view'>");
        sb.append("<div style='margin-bottom:20px;'>");
        sb.append("<div style='font-size:12px;color:#64748b;'>Net Collection from ").append(fromFmt).append(" to ").append(toFmt).append("</div>");
        sb.append("</div>");

        sb.append("<h3 style='font-size:14px;font-weight:bold;margin:16px 0 8px 0;'>Collection Summary</h3>");
        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;' rowspan='2'>User</th>");
        sb.append("<th style='padding:8px 10px;text-align:center;' colspan='3'>Collection</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;' rowspan='2'>Net</th>");
        sb.append("</tr><tr style='background:#525252;color:#fff;'>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#525252;color:#fff;'>Cash</th>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#525252;color:#fff;'>Card</th>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#525252;color:#fff;'>UPI</th>");
        sb.append("</tr></thead><tbody>");

        double tCash=0, tCard=0, tUpi=0, tNet=0;
        for (Map<String, Object> r : summaryRows) {
            String userVal = reportEngine.str(r, "user");
            double cash = reportEngine.doubleVal(r.get("collection_cash"));
            double card = reportEngine.doubleVal(r.get("card"));
            double upi  = reportEngine.doubleVal(r.get("upi"));
            double net = reportEngine.doubleVal(r.get("net"));
            tCash+=cash; tCard+=card; tUpi+=upi; tNet+=net;

            sb.append("<tr>");
            sb.append("<td style='padding:6px 10px;text-align:left;'>");
            sb.append("<a href='#' class='summary-link' onclick=\"showUserDetail('").append(reportEngine.escHtml(userVal)).append("'); return false;\" style='color:#4b5563;text-decoration:none;font-weight:600;cursor:pointer;'>")
              .append(reportEngine.escHtml(userVal)).append("</a>");
            sb.append("</td>");
            tdN(sb, cash); tdN(sb, card); tdN(sb, upi); tdN(sb, net);
            sb.append("</tr>");
        }
        sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
        td(sb, "Total", "left");
        tdN(sb, tCash); tdN(sb, tCard); tdN(sb, tUpi); tdN(sb, tNet);
        sb.append("</tr></tbody></table>");
        // Combined (All Users) Details
        sb.append("<div class='detail-table-title' style='margin-top:30px;font-size:14px;color:#525252;'>Deposits (All Users Combined)</div>");
        buildDetailTableWithTotal(sb, "table-deposits-combined", deposits, 
            new String[]{"deposit_no","dpst_date","patient_no","patient","deposit","bill_date","balance"},
            new String[]{"Deposit No","Dpst Date","Patient No","Patient","Deposit","Bill Date","Balance"},
            "deposit");

        sb.append("<div class='detail-table-title' style='font-size:14px;color:#525252;'>Refunds (All Users Combined)</div>");
        buildDetailTableWithTotal(sb, "table-refunds-combined", refunds, 
            new String[]{"refund_no","refund_date","bill_no","bill_date","patient_no","patient_name","mode","amount","refund_reason"},
            new String[]{"Refund No","Refund Date","Bill No","Bill Date","Patient No","Patient","Mode","Amount (Rs)","Reason"},
            "amount");

        sb.append("<div class='detail-table-title' style='font-size:14px;color:#525252;'>Discounts (All Users Combined)</div>");
        buildDetailTableWithTotal(sb, "table-discounts-combined", discounts, 
            new String[]{"discount_date","bill_no","patient_no","patient","reason","bill_amount","discount","net_amount"},
            new String[]{"Discount Date","Bill No","Patient No","Patient","Reason","Bill Amount","Discount Amount","Net Amount"},
            "bill_amount", "discount", "net_amount");

        // Combined Summary Total
        double totalDepositsCombined = deposits.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("deposit"))).sum();
        double totalRefundsCombined = refunds.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
        double totalDiscountsCombined = discounts.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("discount"))).sum();
        double totalNetCombined = totalDepositsCombined - totalRefundsCombined;

        sb.append("<div class='detail-table-title' style='font-size:14px;color:#525252;'>Summary Total (All Users Combined)</div>");
        sb.append("<table><thead><tr><th style='padding:8px 10px;text-align:left;'>Type</th><th style='padding:8px 10px;text-align:right;'>Amount (Rs)</th></tr></thead><tbody>");
        sb.append("<tr><td style='padding:6px 10px;'>Total Deposits</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalDepositsCombined)).append("</td></tr>");
        sb.append("<tr><td style='padding:6px 10px;'>Total Refunds</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalRefundsCombined)).append("</td></tr>");
        sb.append("<tr><td style='padding:6px 10px;'>Total Discounts</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalDiscountsCombined)).append("</td></tr>");
        sb.append("<tr style='font-weight:bold;background:#f1f5f9;'><td style='padding:8px 10px;'>Net Collection (Deposits - Refunds)</td><td style='padding:8px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalNetCombined)).append("</td></tr>");
        sb.append("</tbody></table>");

        sb.append("</div>"); // end summary-view

        // ── Main Detail View Container ──
        sb.append("<div id='detail-view' style='display:none;'>");
        sb.append("<div style='display:flex;align-items:center;justify-content:space-between;margin-bottom:15px;padding-bottom:8px;border-bottom:2px solid #e2e8f0;'>");
        sb.append("  <h3 style='font-size:15px;font-weight:bold;color:#0f172a;margin:0;'>User Net Collection Details - User: <span id='active-username' style='color:#525252;'></span></h3>");
        sb.append("  <button onclick='goBackToSummary()' style='padding:6px 12px;background:#525252;color:#fff;border:none;border-radius:6px;cursor:pointer;font-weight:600;font-size:13px;display:inline-flex;align-items:center;gap:6px;box-shadow:0 1px 3px rgba(0,0,0,0.1);'><svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><line x1='19' y1='12' x2='5' y2='12'></line><polyline points='12 19 5 12 12 5'></polyline></svg>Back</button>");
        sb.append("</div>");

        for (String user : usernames) {
            sb.append("<div id='details-").append(reportEngine.escHtml(user)).append("' class='user-details-section' style='display:none;'>");

            // User Receipts
            List<Map<String, Object>> userReceipts = receipts.stream().filter(r -> user.equals(r.get("user"))).toList();
            double userReceiptsTotal = userReceipts.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();

            // User Deposits
            List<Map<String, Object>> userDeposits = deposits.stream().filter(r -> user.equals(r.get("user"))).toList();
            double userDepositsTotal = userDeposits.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("deposit"))).sum();
            sb.append("<div class='detail-table-title'>Deposits</div>");
            buildDetailTableWithTotal(sb, "table-deposits-" + user, userDeposits, 
                new String[]{"deposit_no","dpst_date","patient_no","patient","deposit","bill_date","balance"},
                new String[]{"Deposit No","Dpst Date","Patient No","Patient","Deposit","Bill Date","Balance"},
                "deposit");

            // User Refunds
            List<Map<String, Object>> userRefunds = refunds.stream().filter(r -> user.equals(r.get("user"))).toList();
            double userRefundsTotal = userRefunds.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
            sb.append("<div class='detail-table-title'>Refunds</div>");
            buildDetailTableWithTotal(sb, "table-refunds-" + user, userRefunds, 
                new String[]{"refund_no","refund_date","bill_no","bill_date","patient_no","patient_name","mode","amount","refund_reason"},
                new String[]{"Refund No","Refund Date","Bill No","Bill Date","Patient No","Patient","Mode","Amount (Rs)","Reason"},
                "amount");

            // User Discounts
            List<Map<String, Object>> userDiscounts = discounts.stream().filter(r -> user.equals(r.get("user"))).toList();
            double userDiscountsTotal = userDiscounts.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("discount"))).sum();
            sb.append("<div class='detail-table-title'>Discounts</div>");
            buildDetailTableWithTotal(sb, "table-discounts-" + user, userDiscounts, 
                new String[]{"discount_date","bill_no","patient_no","patient","reason","bill_amount","discount","net_amount"},
                new String[]{"Discount Date","Bill No","Patient No","Patient","Reason","Bill Amount","Discount Amount","Net Amount"},
                "bill_amount", "discount", "net_amount");

            // User Summary Total
            sb.append("<div class='detail-table-title'>User Summary Total</div>");
            sb.append("<table><thead><tr><th style='padding:8px 10px;text-align:left;'>Type</th><th style='padding:8px 10px;text-align:right;'>Amount (Rs)</th></tr></thead><tbody>");
            sb.append("<tr><td style='padding:6px 10px;'>Total Deposits</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(userDepositsTotal)).append("</td></tr>");
            sb.append("<tr><td style='padding:6px 10px;'>Total Refunds</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(userRefundsTotal)).append("</td></tr>");
            sb.append("<tr><td style='padding:6px 10px;'>Total Discounts</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(userDiscountsTotal)).append("</td></tr>");
            double userNet = userDepositsTotal - userRefundsTotal;
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'><td style='padding:8px 10px;'>Net Collection (Deposits - Refunds)</td><td style='padding:8px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(userNet)).append("</td></tr>");
            sb.append("</tbody></table>");

            sb.append("</div>");
        }
        sb.append("</div>"); // end detail-view

        // CSS additions
        sb.append("<style>");
        sb.append("  .detail-section { display: none; }");
        sb.append("  .user-details-section { margin-top: 15px; }");
        sb.append("  .detail-table-title { font-size: 13px; font-weight: bold; color: #1e293b; margin: 18px 0 6px 5px; text-transform: uppercase; border-bottom: 1px solid #cbd5e1; padding-bottom: 3px; }");
        sb.append("  .summary-link:hover { text-decoration: underline !important; color: #1f2937 !important; }");
        sb.append("  .pagination-container { display: flex; align-items: center; justify-content: flex-end; margin-top: 8px; margin-bottom: 15px; }");
        sb.append("  .pagination-btn { padding: 4px 10px; background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 4px; color: #334155; cursor: pointer; font-size: 11px; font-weight: 600; }");
        sb.append("  .pagination-btn:hover:not(:disabled) { background: #e2e8f0; color: #0f172a; }");
        sb.append("  .pagination-btn:disabled { opacity: 0.5; cursor: not-allowed; }");
        sb.append("  .page-info { font-size: 12px; margin: 0 10px; font-weight: 600; color: #475569; }");
        sb.append("</style>");

        // JS execution wrapper via onerror
        sb.append("<img src='1' onerror=\"")
          .append("window.goBackToSummary = function() {")
          .append("  document.getElementById('detail-view').style.display = 'none';")
          .append("  document.getElementById('summary-view').style.display = 'block';")
          .append("  window.scrollTo(0, 0);")
          .append("};")
          .append("window.showUserDetail = function(username) {")
          .append("  document.getElementById('summary-view').style.display = 'none';")
          .append("  document.getElementById('detail-view').style.display = 'block';")
          .append("  document.getElementById('active-username').innerText = username;")
          .append("  var divs = document.querySelectorAll('.user-details-section');")
          .append("  divs.forEach(function(div) { div.style.display = 'none'; });")
          .append("  var target = document.getElementById('details-' + username);")
          .append("  if (target) { target.style.display = 'block'; }")
          .append("  window.scrollTo(0, 0);")
          .append("  if (!window.paginatedTables) window.paginatedTables = {};")
          .append("  var types = ['deposits', 'refunds', 'discounts'];")
          .append("  types.forEach(function(type) {")
          .append("    var tableId = 'table-' + type + '-' + username;")
          .append("    if (!window.paginatedTables[tableId]) {")
          .append("      window.paginateTable(tableId, 10);")
          .append("      window.paginatedTables[tableId] = true;")
          .append("    }")
          .append("  });")
          .append("};")
          .append("window.paginateTable = function(tableId, pageSize) {")
          .append("  var table = document.getElementById(tableId);")
          .append("  if (!table) return;")
          .append("  var tbody = table.querySelector('tbody');")
          .append("  if (!tbody) return;")
          .append("  var rows = Array.from(tbody.querySelectorAll('tr'));")
          .append("  var dataRows = rows.filter(function(r) { return r.getAttribute('data-total-row') !== 'true'; });")
          .append("  var totalRow = rows.find(function(r) { return r.getAttribute('data-total-row') === 'true'; });")
          .append("  var totalPages = Math.ceil(dataRows.length / pageSize);")
          .append("  if (totalPages <= 1) return;")
          .append("  var currentPage = 1;")
          .append("  function showPage(page) {")
          .append("    currentPage = page;")
          .append("    var start = (page - 1) * pageSize;")
          .append("    var end = start + pageSize;")
          .append("    dataRows.forEach(function(row, idx) { row.style.display = (idx >= start && idx < end) ? '' : 'none'; });")
          .append("    if (totalRow) totalRow.style.display = '';")
          .append("    var controls = document.getElementById(tableId + '-controls');")
          .append("    if (controls) {")
          .append("      controls.querySelector('.page-info').innerText = 'Page ' + currentPage + ' of ' + totalPages;")
          .append("      controls.querySelector('.prev-btn').disabled = (currentPage === 1);")
          .append("      controls.querySelector('.next-btn').disabled = (currentPage === totalPages);")
          .append("    }")
          .append("  }")
          .append("  var controlsDiv = document.createElement('div');")
          .append("  controlsDiv.id = tableId + '-controls';")
          .append("  controlsDiv.className = 'pagination-container';")
          .append("  var btnPrev = document.createElement('button');")
          .append("  btnPrev.className = 'pagination-btn prev-btn';")
          .append("  btnPrev.style.marginRight = '5px';")
          .append("  btnPrev.innerText = 'Prev';")
          .append("  var spanInfo = document.createElement('span');")
          .append("  spanInfo.className = 'page-info';")
          .append("  spanInfo.innerText = 'Page 1';")
          .append("  var btnNext = document.createElement('button');")
          .append("  btnNext.className = 'pagination-btn next-btn';")
          .append("  btnNext.style.marginLeft = '5px';")
          .append("  btnNext.innerText = 'Next';")
          .append("  controlsDiv.appendChild(btnPrev);")
          .append("  controlsDiv.appendChild(spanInfo);")
          .append("  controlsDiv.appendChild(btnNext);")
          .append("  btnPrev.onclick = function() { if (currentPage > 1) showPage(currentPage - 1); };")
          .append("  btnNext.onclick = function() { if (currentPage < totalPages) showPage(currentPage + 1); };")
          .append("  table.parentNode.insertBefore(controlsDiv, table.nextSibling);")
          .append("  showPage(1);")
          .append("};")
          .append("var combinedTypes = ['deposits', 'refunds', 'discounts'];")
          .append("combinedTypes.forEach(function(type) {")
          .append("  var tableId = 'table-' + type + '-combined';")
          .append("  if (!window.paginatedTables[tableId]) {")
          .append("    window.paginateTable(tableId, 10);")
          .append("    window.paginatedTables[tableId] = true;")
          .append("  }")
          .append("});")
          .append("\" style='display:none;'>");

        sb.append("</div>");
        return sb.toString();
    }

    private void buildDetailTableWithTotal(StringBuilder sb, String tableId, List<Map<String, Object>> rows, String[] keys, String[] headers, String... totalKeys) {
        sb.append("<table id='").append(reportEngine.escHtml(tableId)).append("'><thead><tr>");
        for (String h : headers) {
            sb.append("<th style='padding:8px 10px;text-align:left;'>").append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='").append(headers.length).append("' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            java.util.Map<String, Double> totals = new java.util.HashMap<>();
            for (String tk : totalKeys) {
                totals.put(tk, 0.0);
            }
            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                for (String k : keys) {
                    Object v = r.get(k);
                    if (totals.containsKey(k)) {
                        totals.put(k, totals.get(k) + reportEngine.doubleVal(v));
                    }
                    String val = (v instanceof java.sql.Date || v instanceof java.time.LocalDate)
                            ? reportEngine.formatDateValue(v) : reportEngine.formatGeneralValue(v);
                    sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(val)).append("</td>");
                }
                sb.append("</tr>");
            }
            if (totalKeys.length > 0) {
                sb.append("<tr style='font-weight:bold;background:#f1f5f9;' data-total-row='true'>");
                int firstTotalIdx = -1;
                for (int i = 0; i < keys.length; i++) {
                    if (totals.containsKey(keys[i])) {
                        firstTotalIdx = i;
                        break;
                    }
                }
                for (int i = 0; i < keys.length; i++) {
                    String k = keys[i];
                    if (totals.containsKey(k)) {
                        sb.append("<td style='padding:8px 10px;font-weight:bold;'>").append(reportEngine.formatGeneralValue(totals.get(k))).append("</td>");
                    } else if (i == firstTotalIdx - 1) {
                        sb.append("<td style='padding:8px 10px;text-align:right;font-weight:bold;'>Total:</td>");
                    } else {
                        sb.append("<td></td>");
                    }
                }
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table>");
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
        buildDetailTable(sb, rows, new String[]{"rcpt_date","receipt_no","bill_date","bill_no","patient_no","patient","age_sex","consultant","encounter_type","mode","amount","user"},
                new String[]{"Receipt Date","Receipt No","Bill Date","Bill No","Patient No","Patient","Age/Sex","Consultant","Encounter Type","Mode","Amount (Rs)","User"});
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
        buildDetailTable(sb, rows, new String[]{"dpst_date","deposit_no","bill_date","adj_against_bill","patient_no","patient","age_sex","consultant","encounter_type","deposit","user"},
                new String[]{"Deposit Date","Deposit No","Bill Date","Bill No","Patient No","Patient","Age/Sex","Consultant","Encounter Type","Deposit","User"});
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
        String[] headers = {"Refund Date","Refund No","Bill Date","Bill No","Patient No","Patient","Age/Sex","Consultant","Encounter Type","Mode","Reason for Refund","Amount (Rs)","User"};
        for(String h: headers) sb.append("<th style='padding:8px 10px;text-align:left;'>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='13' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            double totalAmount = 0;
            String[] keys = {"refund_date","refund_no","bill_date","bill_no","patient_no","patient_name","age_sex","consultant","encounter_type","mode","refund_reason","amount","user"};
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
            sb.append("<td colspan='11' style='text-align:right;padding:6px 10px;'>Total : Rs.</td>");
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
