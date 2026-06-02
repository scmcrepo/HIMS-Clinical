package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PharmacyReportService extends BaseReportService {

    private final PharmacyReportDataService pharmacyReportDataService;

    public PharmacyReportService(ReportEngine reportEngine, PharmacyReportDataService pharmacyReportDataService) {
        super(reportEngine);
        this.pharmacyReportDataService = pharmacyReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "pharmacy_sales_bills", "description", "Date / Period-wise Pharmacy Sales Bills", "category", "Pharmacy"),
        Map.of("name", "pharmacy_sales_collection", "description", "Sales Collection Report", "category", "Pharmacy"),
        Map.of("name", "stock_ledger", "description", "Stock Ledger Report (Legacy)", "category", "Pharmacy"),
        Map.of("name", "bill_detail", "description", "Bill Detail Report (Legacy)", "category", "Pharmacy")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        m.put("pharmacy_sales_bills", DATE_RANGE_PARAMS);
        m.put("pharmacy_sales_collection", DATE_RANGE_PARAMS);
        m.put("stock_ledger", List.of(
            param("departmentId", "DEPARTMENT", true, "", "Department")
        ));
        m.put("bill_detail", List.of(
            param("billId", "UUID", true, "", "Bill ID")
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
            case "pharmacy_sales_bills" -> pharmacyReportDataService.getPharmacySalesBillsReport(from, to);
            case "pharmacy_sales_collection" -> pharmacyReportDataService.getPharmacySalesCollectionSummary(from, to);
            case "stock_ledger" -> pharmacyReportDataService.getStockLedger(reportEngine.uuid(params, "departmentId"));
            case "bill_detail" -> List.of(pharmacyReportDataService.getBillDetail(reportEngine.uuid(params, "billId")));
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if ("pharmacy_sales_collection".equals(reportName)) {
            return buildPharmacySalesCollectionHtml(rows, params);
        }
        return null;
    }

    private String buildPharmacySalesCollectionHtml(List<Map<String, Object>> summaryRows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");
        String fromFmt = fmtDate(from);
        String toFmt = fmtDate(to);

        StringBuilder sb = new StringBuilder();
        sb.append("<style>");
        sb.append("body { font-family: sans-serif; color: #1e293b; margin: 0; }");
        sb.append("table { border-collapse: collapse; width: 100%; font-size: 12px; margin-bottom: 20px; }");
        sb.append("th { background: #5a7b8f; color: #fff; padding: 8px 10px; border: 1px solid #cbd5e1; font-weight: 600; text-align: left; }");
        sb.append("td { padding: 6px 10px; border: 1px solid #cbd5e1; text-align: left; }");
        sb.append(".text-right { text-align: right; }");
        sb.append(".text-center { text-align: center; }");
        sb.append("</style>");

        // Header
        sb.append("<div style='text-align: left; margin-bottom: 20px;'>");
        sb.append("  <h2 style='margin: 0; color: #1e293b; font-size: 22px; font-weight: 700;'>Sales Collection Report</h2>");
        sb.append("  <div style='margin-top: 6px; color: #475569; font-size: 13px; font-weight: 500;'>Sales Collection from ").append(fromFmt).append(" to ").append(toFmt).append("</div>");
        sb.append("</div>");

        // 1. Collection Summary
        sb.append("<h3 style='font-size:14px;font-weight:bold;margin:20px 0 8px 0;'>Collection Summary</h3>");
        sb.append("<table>");
        sb.append("<thead>");
        sb.append("<tr>");
        sb.append("<th rowspan='2' style='text-align:left;'>Name</th>");
        sb.append("<th colspan='4' style='text-align:center;'>Receipts</th>");
        sb.append("<th rowspan='2' style='text-align:right;'>Refund (Cash)</th>");
        sb.append("<th rowspan='2' style='text-align:right;'>Net Amount</th>");
        sb.append("</tr>");
        sb.append("<tr>");
        sb.append("<th style='text-align:right; background: #5a7b8f;'>Cash</th>");
        sb.append("<th style='text-align:right; background: #5a7b8f;'>Card</th>");
        sb.append("<th style='text-align:right; background: #5a7b8f;'>Cheque</th>");
        sb.append("<th style='text-align:right; background: #5a7b8f;'>Net</th>");
        sb.append("</tr>");
        sb.append("</thead>");
        sb.append("<tbody>");

        double tCash = 0, tCard = 0, tCheque = 0, tNet = 0, tRefund = 0, tNetAmount = 0;
        if (summaryRows.isEmpty()) {
            sb.append("<tr><td colspan='7' style='text-align:center;color:#64748b;font-style:italic;'>No records found</td></tr>");
        } else {
            for (Map<String, Object> r : summaryRows) {
                String userName = reportEngine.str(r, "user_name");
                double cash = reportEngine.doubleVal(r.get("cash"));
                double card = reportEngine.doubleVal(r.get("card"));
                double cheque = reportEngine.doubleVal(r.get("cheque"));
                double net = reportEngine.doubleVal(r.get("net"));
                double refund = reportEngine.doubleVal(r.get("refund_cash"));
                double netAmnt = reportEngine.doubleVal(r.get("net_amount"));

                tCash += cash; tCard += card; tCheque += cheque; tNet += net;
                tRefund += refund; tNetAmount += netAmnt;

                sb.append("<tr>");
                sb.append("<td>").append(reportEngine.escHtml(userName)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(cash)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(card)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(cheque)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(net)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(refund)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(netAmnt)).append("</td>");
                sb.append("</tr>");
            }
            // Totals row
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
            sb.append("<td>Total</td>");
            sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(tCash)).append("</td>");
            sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(tCard)).append("</td>");
            sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(tCheque)).append("</td>");
            sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(tNet)).append("</td>");
            sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(tRefund)).append("</td>");
            sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(tNetAmount)).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody>");
        sb.append("</table>");

        // 2. Sales Collection Detail
        sb.append("<h3 style='font-size:14px;font-weight:bold;margin:25px 0 10px 0;'>Sales Collection Detail</h3>");
        
        // Receipts Section
        sb.append("<div style='font-size:13px;font-weight:bold;margin:0 0 8px 0;'>Receipts</div>");
        sb.append("<table>");
        sb.append("<thead>");
        sb.append("<tr>");
        sb.append("<th>Receipt No</th>");
        sb.append("<th>Rcpt Date</th>");
        sb.append("<th>Patient No</th>");
        sb.append("<th>Patient</th>");
        sb.append("<th style='text-align:right;'>Cash</th>");
        sb.append("<th style='text-align:right;'>Cheque</th>");
        sb.append("<th style='text-align:right;'>Card</th>");
        sb.append("<th>User</th>");
        sb.append("</tr>");
        sb.append("</thead>");
        sb.append("<tbody>");

        List<Map<String, Object>> receipts = pharmacyReportDataService.getPharmacySalesCollectionReceipts(from, to);
        double sumCash = 0, sumCheque = 0, sumCard = 0;
        if (receipts.isEmpty()) {
            sb.append("<tr><td colspan='8' style='text-align:center;color:#64748b;font-style:italic;'>No records found</td></tr>");
        } else {
            for (Map<String, Object> r : receipts) {
                String receiptNo = reportEngine.str(r, "receipt_no");
                String rcptDate = fmtDate(reportEngine.str(r, "rcpt_date"));
                String patientNo = reportEngine.str(r, "patient_no");
                String patientName = reportEngine.str(r, "patient");
                double cash = reportEngine.doubleVal(r.get("cash"));
                double cheque = reportEngine.doubleVal(r.get("cheque"));
                double card = reportEngine.doubleVal(r.get("card"));
                String user = reportEngine.str(r, "user_name");

                sumCash += cash; sumCheque += cheque; sumCard += card;

                sb.append("<tr>");
                sb.append("<td>").append(reportEngine.escHtml(receiptNo)).append("</td>");
                sb.append("<td>").append(reportEngine.escHtml(rcptDate)).append("</td>");
                sb.append("<td>").append(reportEngine.escHtml(patientNo)).append("</td>");
                sb.append("<td>").append(reportEngine.escHtml(patientName)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(cash)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(cheque)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(card)).append("</td>");
                sb.append("<td>").append(reportEngine.escHtml(user)).append("</td>");
                sb.append("</tr>");
            }
        }
        // Total(A) row with dashed border
        sb.append("<tr>");
        sb.append("<td colspan='4' style='text-align:right;font-weight:bold;border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'>Total(A) : Rs.</td>");
        sb.append("<td class='text-right' style='font-weight:bold;border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'>").append(reportEngine.formatGeneralValue(sumCash)).append("</td>");
        sb.append("<td class='text-right' style='font-weight:bold;border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'>").append(reportEngine.formatGeneralValue(sumCheque)).append("</td>");
        sb.append("<td class='text-right' style='font-weight:bold;border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'>").append(reportEngine.formatGeneralValue(sumCard)).append("</td>");
        sb.append("<td style='border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'></td>");
        sb.append("</tr>");
        sb.append("</tbody>");
        sb.append("</table>");

        // Refunds Section
        sb.append("<div style='font-size:13px;font-weight:bold;margin:20px 0 8px 0;'>Refunds</div>");
        sb.append("<table>");
        sb.append("<thead>");
        sb.append("<tr>");
        sb.append("<th>Receipt No</th>");
        sb.append("<th>Rcpt Date</th>");
        sb.append("<th>Patient No</th>");
        sb.append("<th>Patient</th>");
        sb.append("<th style='text-align:right;'>Cash</th>");
        sb.append("<th style='text-align:right;'>Cheque</th>");
        sb.append("<th style='text-align:right;'>Card</th>");
        sb.append("<th>User</th>");
        sb.append("</tr>");
        sb.append("</thead>");
        sb.append("<tbody>");

        List<Map<String, Object>> refunds = pharmacyReportDataService.getPharmacySalesCollectionRefunds(from, to);
        double sumRefundCash = 0, sumRefundCheque = 0, sumRefundCard = 0;
        if (refunds.isEmpty()) {
            sb.append("<tr><td colspan='8' style='text-align:center;color:#64748b;font-style:italic;'>No records found</td></tr>");
        } else {
            for (Map<String, Object> r : refunds) {
                String receiptNo = reportEngine.str(r, "receipt_no");
                String rcptDate = fmtDate(reportEngine.str(r, "rcpt_date"));
                String patientNo = reportEngine.str(r, "patient_no");
                String patientName = reportEngine.str(r, "patient");
                double cash = reportEngine.doubleVal(r.get("cash"));
                double cheque = reportEngine.doubleVal(r.get("cheque"));
                double card = reportEngine.doubleVal(r.get("card"));
                String user = reportEngine.str(r, "user_name");

                sumRefundCash += cash; sumRefundCheque += cheque; sumRefundCard += card;

                sb.append("<tr>");
                sb.append("<td>").append(reportEngine.escHtml(receiptNo)).append("</td>");
                sb.append("<td>").append(reportEngine.escHtml(rcptDate)).append("</td>");
                sb.append("<td>").append(reportEngine.escHtml(patientNo)).append("</td>");
                sb.append("<td>").append(reportEngine.escHtml(patientName)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(cash)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(cheque)).append("</td>");
                sb.append("<td class='text-right'>").append(reportEngine.formatGeneralValue(card)).append("</td>");
                sb.append("<td>").append(reportEngine.escHtml(user)).append("</td>");
                sb.append("</tr>");
            }
        }
        // Total(B) row with dashed border
        sb.append("<tr>");
        sb.append("<td colspan='4' style='text-align:right;font-weight:bold;border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'>Total(B) : Rs.</td>");
        sb.append("<td class='text-right' style='font-weight:bold;border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'>").append(reportEngine.formatGeneralValue(sumRefundCash)).append("</td>");
        sb.append("<td class='text-right' style='font-weight:bold;border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'>").append(reportEngine.formatGeneralValue(sumRefundCheque)).append("</td>");
        sb.append("<td class='text-right' style='font-weight:bold;border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'>").append(reportEngine.formatGeneralValue(sumRefundCard)).append("</td>");
        sb.append("<td style='border-left:none;border-right:none;border-top:1px dashed #94a3b8;border-bottom:1px dashed #94a3b8;'></td>");
        sb.append("</tr>");

        // Net Collection(A)-(B) row
        double netCash = sumCash + sumRefundCash;
        double netCheque = sumCheque + sumRefundCheque;
        double netCard = sumCard + sumRefundCard;
        
        sb.append("<tr style='font-weight:bold;font-size:13px;'>");
        sb.append("<td colspan='4' style='text-align:right;padding:10px 8px;border:none;'>Net Collection(A)-(B) =</td>");
        sb.append("<td class='text-right' style='padding:10px 8px;border:none;'>").append(reportEngine.formatGeneralValue(netCash)).append("</td>");
        sb.append("<td class='text-right' style='padding:10px 8px;border:none;'>").append(reportEngine.formatGeneralValue(netCheque)).append("</td>");
        sb.append("<td class='text-right' style='padding:10px 8px;border:none;'>").append(reportEngine.formatGeneralValue(netCard)).append("</td>");
        sb.append("<td style='border:none;'></td>");
        sb.append("</tr>");

        sb.append("</tbody>");
        sb.append("</table>");

        return sb.toString();
    }

    private String fmtDate(String iso) {
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(iso);
            return d.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception e) { return iso; }
    }
}
