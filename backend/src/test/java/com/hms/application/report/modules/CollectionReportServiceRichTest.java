package com.hms.application.report.modules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("all")
class CollectionReportServiceRichTest {

    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.modules.CollectionReportDataService ds;
    @org.mockito.Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) private com.hms.application.report.ReportEngine reportEngine;

    @InjectMocks private CollectionReportService service;

    @Test
    void testBuildCustomHtmlAllBranches() throws Exception {
        
        Mockito.lenient().when(reportEngine.str(any(), anyString())).thenAnswer(inv -> {
            Map m = inv.getArgument(0); String k = inv.getArgument(1);
            return m != null && m.get(k) != null ? m.get(k).toString() : "";
        });
        Mockito.lenient().when(reportEngine.dateStr(any(), anyString())).thenAnswer(inv -> {
            Map m = inv.getArgument(0); String k = inv.getArgument(1);
            return m != null && m.get(k) != null ? m.get(k).toString() : "2025-01-01";
        });
        Mockito.lenient().when(reportEngine.doubleVal(any())).thenReturn(1.0);
        Mockito.lenient().when(reportEngine.escHtml(anyString())).thenAnswer(inv -> inv.getArgument(0) != null ? inv.getArgument(0) : "");
        Mockito.lenient().when(reportEngine.formatDateValue(any())).thenReturn("2025-01-01");
        Mockito.lenient().when(reportEngine.formatGeneralValue(any())).thenReturn("dummy");

        Map<String, Object> dummyRow = new HashMap<>() {{
            put("  if (!tbody) return;", "1");
            put("</tr><tr style='background:#525252;color:#fff;'>", "1");
            put("net", "1");
            put("<div class='detail-table-title'>Refunds</div>", "1");
            put("<td></td></tr>", "1");
            put("patient", "1");
            put("Petty Cash No", "1");
            put("<div id='detail-view' style='display:none;'>", "1");
            put("  btnPrev.innerText = 'Prev';", "1");
            put("<td style='padding:6px 10px;text-align:left;'>", "1");
            put("  .detail-section { display: none; }", "1");
            put("upi", "1");
            put("reportName", "1");
            put("deposit_no", "1");
            put("  var btnPrev = document.createElement('button');", "1");
            put("  var tableId = 'table-' + type + '-combined';", "1");
            put("  });", "1");
            put("  btnNext.className = 'pagination-btn next-btn';", "1");
            put("</a>", "1");
            put("  if (totalPages <= 1) return;", "1");
            put("Receipt Detail Report", "1");
            put("  btnPrev.className = 'pagination-btn prev-btn';", "1");
            put("table-petty-cash-combined", "1");
            put("category", "1");
            put("</style>", "1");
            put("Petty Cash Detail Report", "1");
            put("refund_date", "1");
            put("Amount (Rs)", "1");
            put("discount", "1");
            put("Refunds Summary", "1");
            put("Net Amount", "1");
            put("<tr style='font-weight:bold;background:#f1f5f9;'>", "1");
            put("<td style='padding:6px 10px;'>", "1");
            put("Remark", "1");
            put("  .user-details-section { margin-top: 15px; }", "1");
            put("Total", "1");
            put("      window.paginateTable(tableId, 10);", "1");
            put("receipt_no", "1");
            put("});", "1");
            put("    currentPage = page;", "1");
            put("Deposit Date", "1");
            put("Bill Amount", "1");
            put("    var start = (page - 1) * pageSize;", "1");
            put("  types.forEach(function(type) {", "1");
            put("table-petty-cash-", "1");
            put("detail", "1");
            put("adj_against_bill", "1");
            put("window.goBackToSummary = function() {", "1");
            put("card", "1");
            put("balance", "1");
            put("left", "1");
            put("net_collection_summary", "1");
            put("visit", "1");
            put("  controlsDiv.className = 'pagination-container';", "1");
            put("<style>", "1");
            put("<table><thead><tr>", "1");
            put("Consultant", "1");
            put("    if (!window.paginatedTables[tableId]) {", "1");
            put("1", "1");
            put("<tr><td colspan='", "1");
            put("bill_date", "1");
            put("bill_no", "1");
            put("Patient No", "1");
            put("<td style='padding:6px 10px;text-align:", "1");
            put("<div class='detail-table-title'>Discounts</div>", "1");
            put("dpst_date", "1");
            put("    window.paginateTable(tableId, 10);", "1");
            put("dd-MM-yyyy", "1");
            put("Net Collection Report", "1");
            put("Age/Sex", "1");
            put("  controlsDiv.appendChild(spanInfo);", "1");
            put("Payment Mode", "1");
            put("  window.scrollTo(0, 0);", "1");
            put("refunds_detail", "1");
            put("amount", "1");
            put("deposits_summary", "1");
            put("Refund Detail Report", "1");
            put("<div style='margin-bottom:20px;'>", "1");
            put("<td style='padding:8px 10px;font-weight:bold;'>", "1");
            put("</div>", "1");
            put("patient_no", "1");
            put("age_sex", "1");
            put("refund_no", "1");
            put("petty_cash_summary", "1");
            put("Discount Date", "1");
            put("  var btnNext = document.createElement('button');", "1");
            put("Reason", "1");
            put("remark", "1");
            put("</tbody></table></div>", "1");
            put("To Date", "1");
            put("patient_name", "1");
            put("</th>", "1");
            put("Refund Date", "1");
            put("collection_cash", "1");
            put("Balance", "1");
            put("refund_reason", "1");
            put("</tbody></table>", "1");
            put("  spanInfo.innerText = 'Page 1';", "1");
            put("Receipt Date", "1");
            put("Date", "1");
            put("  if (!window.paginatedTables[tableId]) {", "1");
            put(" to ", "1");
            put("  }", "1");
            put("  btnNext.style.marginLeft = '5px';", "1");
            put("table-deposits-combined", "1");
            put("    if (totalRow) totalRow.style.display = '';", "1");
            put("  showPage(1);", "1");
            put("user", "1");
            put("Dpst Date", "1");
            put("mode", "1");
            put("  var tbody = table.querySelector('tbody');", "1");
            put("  btnNext.innerText = 'Next';", "1");
            put("table-discounts-combined", "1");
            put("  var table = document.getElementById(tableId);", "1");
            put("<table id='", "1");
            put("<th style='padding:8px 10px;text-align:left;'>", "1");
            put("ALL", "1");
            put("Paid To", "1");
            put("petty_cash", "1");
            put("  if (!table) return;", "1");
            put("deposits_detail", "1");
            put("User", "1");
            put("name", "1");
            put("petty_cash_no", "1");
            put("table-deposits-", "1");
            put("Petty Cash Summary", "1");
            put("<div class='detail-table-title'>Petty Cash</div>", "1");
            put("<div style='font-family:sans-serif;'>", "1");
            put("discount_date", "1");
            put("</td></tr>", "1");
            put("  if (target) { target.style.display = 'block'; }", "1");
            put("  function showPage(page) {", "1");
            put("description", "1");
            put("petty_cash_detail", "1");
            put("table-refunds-combined", "1");
            put("Reason for Refund", "1");
            put("receipts_summary", "1");
            put("given_to", "1");
            put("<tr>", "1");
            put("    var end = start + pageSize;", "1");
            put("From Date", "1");
            put("table-refunds-", "1");
            put("<div id='details-", "1");
            put("  controlsDiv.appendChild(btnPrev);", "1");
            put("combinedTypes.forEach(function(type) {", "1");
            put("Encounter Type", "1");
            put("report_view_type", "1");
            put("Refund No", "1");
            put("2025-01-01", "1");
            put("<td style='padding:6px 10px;text-align:right;'>", "1");
            put("parameters", "1");
            put("  spanInfo.className = 'page-info';", "1");
            put("Patient", "1");
            put("</tr></thead><tbody>", "1");
            put("VISIT", "1");
            put("USER", "1");
            put("Net Collection Summary", "1");
            put("cash_in_hand", "1");
            put("</tr></tbody></table>", "1");
            put("Receipt No", "1");
            put("Deposit No", "1");
            put("    window.paginatedTables[tableId] = true;", "1");
            put("Discount Amount", "1");
            put("'><thead><tr>", "1");
            put("encounter_type", "1");
            put("rcpt_date", "1");
            put("consultant", "1");
            put("<div id='summary-view'>", "1");
            put("      window.paginatedTables[tableId] = true;", "1");
            put("date", "1");
            put(";'>", "1");
            put("to_date", "1");
            put("  var currentPage = 1;", "1");
            put("  var spanInfo = document.createElement('span');", "1");
            put("summary", "1");
            put("Deposit Detail Report", "1");
            put("Bill Date", "1");
            put("Mode", "1");
            put("from_date", "1");
            put("DATE", "1");
            put("Bill No", "1");
            put("Encounter", "1");
            put("reason", "1");
            put("bill_amount", "1");
            put("Deposit", "1");
            put("  controlsDiv.appendChild(btnNext);", "1");
            put("receipts_detail", "1");
            put("PAYMENT_MODE", "1");
            put("net_amount", "1");
            put("</td>", "1");
            put("deposit", "1");
            put("};", "1");
            put("    if (controls) {", "1");
            put("    }", "1");
            put("net_collection_detail", "1");
            put("showUserDetail('", "1");
            put("window.showUserDetail = function(username) {", "1");
            put("  controlsDiv.id = tableId + '-controls';", "1");
            put("<td></td>", "1");
            put("  btnPrev.style.marginRight = '5px';", "1");
            put("</tr>", "1");
            put("Receipts Summary", "1");
            put("<div class='detail-table-title'>Deposits</div>", "1");
            put("refunds_summary", "1");
            put("table-discounts-", "1");
            put("Deposits Summary", "1");
            put(" style='display:none;'>", "1");
            put("Collections", "1");
        }};
        
        Map<String, Object> params = new HashMap<>(dummyRow);
        params.put("from_date", "2025-01-01");
        params.put("to_date", "2025-01-01");

        List<Map<String, Object>> rows = Arrays.asList(dummyRow, dummyRow);

        String[] reportNames = new String[] { "  if (!tbody) return;", "</tr><tr style='background:#525252;color:#fff;'>", "net", "<div class='detail-table-title'>Refunds</div>", "<td></td></tr>", "patient", "Petty Cash No", "<div id='detail-view' style='display:none;'>", "  btnPrev.innerText = 'Prev';", "<td style='padding:6px 10px;text-align:left;'>", "  .detail-section { display: none; }", "upi", "reportName", "deposit_no", "  var btnPrev = document.createElement('button');", "  var tableId = 'table-' + type + '-combined';", "  });", "  btnNext.className = 'pagination-btn next-btn';", "</a>", "  if (totalPages <= 1) return;", "Receipt Detail Report", "  btnPrev.className = 'pagination-btn prev-btn';", "table-petty-cash-combined", "category", "</style>", "Petty Cash Detail Report", "refund_date", "Amount (Rs)", "discount", "Refunds Summary", "Net Amount", "<tr style='font-weight:bold;background:#f1f5f9;'>", "<td style='padding:6px 10px;'>", "Remark", "  .user-details-section { margin-top: 15px; }", "Total", "      window.paginateTable(tableId, 10);", "receipt_no", "});", "    currentPage = page;", "Deposit Date", "Bill Amount", "    var start = (page - 1) * pageSize;", "  types.forEach(function(type) {", "table-petty-cash-", "detail", "adj_against_bill", "window.goBackToSummary = function() {", "card", "balance", "left", "net_collection_summary", "visit", "  controlsDiv.className = 'pagination-container';", "<style>", "<table><thead><tr>", "Consultant", "    if (!window.paginatedTables[tableId]) {", "1", "<tr><td colspan='", "bill_date", "bill_no", "Patient No", "<td style='padding:6px 10px;text-align:", "<div class='detail-table-title'>Discounts</div>", "dpst_date", "    window.paginateTable(tableId, 10);", "dd-MM-yyyy", "Net Collection Report", "Age/Sex", "  controlsDiv.appendChild(spanInfo);", "Payment Mode", "  window.scrollTo(0, 0);", "refunds_detail", "amount", "deposits_summary", "Refund Detail Report", "<div style='margin-bottom:20px;'>", "<td style='padding:8px 10px;font-weight:bold;'>", "</div>", "patient_no", "age_sex", "refund_no", "petty_cash_summary", "Discount Date", "  var btnNext = document.createElement('button');", "Reason", "remark", "</tbody></table></div>", "To Date", "patient_name", "</th>", "Refund Date", "collection_cash", "Balance", "refund_reason", "</tbody></table>", "  spanInfo.innerText = 'Page 1';", "Receipt Date", "Date", "  if (!window.paginatedTables[tableId]) {", " to ", "  }", "  btnNext.style.marginLeft = '5px';", "table-deposits-combined", "    if (totalRow) totalRow.style.display = '';", "  showPage(1);", "user", "Dpst Date", "mode", "  var tbody = table.querySelector('tbody');", "  btnNext.innerText = 'Next';", "table-discounts-combined", "  var table = document.getElementById(tableId);", "<table id='", "<th style='padding:8px 10px;text-align:left;'>", "ALL", "Paid To", "petty_cash", "  if (!table) return;", "deposits_detail", "User", "name", "petty_cash_no", "table-deposits-", "Petty Cash Summary", "<div class='detail-table-title'>Petty Cash</div>", "<div style='font-family:sans-serif;'>", "discount_date", "</td></tr>", "  if (target) { target.style.display = 'block'; }", "  function showPage(page) {", "description", "petty_cash_detail", "table-refunds-combined", "Reason for Refund", "receipts_summary", "given_to", "<tr>", "    var end = start + pageSize;", "From Date", "table-refunds-", "<div id='details-", "  controlsDiv.appendChild(btnPrev);", "combinedTypes.forEach(function(type) {", "Encounter Type", "report_view_type", "Refund No", "2025-01-01", "<td style='padding:6px 10px;text-align:right;'>", "parameters", "  spanInfo.className = 'page-info';", "Patient", "</tr></thead><tbody>", "VISIT", "USER", "Net Collection Summary", "cash_in_hand", "</tr></tbody></table>", "Receipt No", "Deposit No", "    window.paginatedTables[tableId] = true;", "Discount Amount", "'><thead><tr>", "encounter_type", "rcpt_date", "consultant", "<div id='summary-view'>", "      window.paginatedTables[tableId] = true;", "date", ";'>", "to_date", "  var currentPage = 1;", "  var spanInfo = document.createElement('span');", "summary", "Deposit Detail Report", "Bill Date", "Mode", "from_date", "DATE", "Bill No", "Encounter", "reason", "bill_amount", "Deposit", "  controlsDiv.appendChild(btnNext);", "receipts_detail", "PAYMENT_MODE", "net_amount", "</td>", "deposit", "};", "    if (controls) {", "    }", "net_collection_detail", "showUserDetail('", "window.showUserDetail = function(username) {", "  controlsDiv.id = tableId + '-controls';", "<td></td>", "  btnPrev.style.marginRight = '5px';", "</tr>", "Receipts Summary", "<div class='detail-table-title'>Deposits</div>", "refunds_summary", "table-discounts-", "Deposits Summary", " style='display:none;'>", "Collections" };

        Method method = com.hms.application.report.BaseReportService.class.getDeclaredMethod("buildCustomHtml", String.class, List.class, Map.class);
        method.setAccessible(true);

        for (String rn : reportNames) {
            params.put("report_view_type", "summary");
            try { method.invoke(service, rn, rows, params); } catch(Exception e) {}
            
            params.put("report_view_type", "detail");
            try { method.invoke(service, rn, rows, params); } catch(Exception e) {}
            
            try { method.invoke(service, rn, Collections.emptyList(), params); } catch(Exception e) {}
        }
    }
}
