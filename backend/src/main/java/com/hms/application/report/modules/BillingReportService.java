package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BillingReportService extends BaseReportService {

    private final BillingReportDataService billingReportDataService;

    public BillingReportService(ReportEngine reportEngine, BillingReportDataService billingReportDataService) {
        super(reportEngine);
        this.billingReportDataService = billingReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "bills_raised_daywise", "description", "Bills Raised Summary", "category", "Billing"),
        Map.of("name", "bills_cancelled_daywise", "description", "Date-wise Bills Cancelled", "category", "Billing"),
        Map.of("name", "discount_report", "description", "Discount Report", "category", "Billing"),
        Map.of("name", "bills_overdue", "description", "IP Overdue Bills Report", "category", "Billing"),
        Map.of("name", "unsettled_bills", "description", "Unsettled Bills Report", "category", "Billing"),
        Map.of("name", "bill_raised_summary", "description", "Bill Raised Summary", "category", "Billing"),
        Map.of("name", "bill_cancelled_summary", "description", "Cancelled Bills Summary", "category", "Billing"),
        Map.of("name", "discount_summary", "description", "Discounts Summary", "category", "Billing"),
        Map.of("name", "outstanding_bills_summary", "description", "Outstanding Bills Summary", "category", "Billing"),
        Map.of("name", "ip_outstanding_bills_summary", "description", "IP Outstanding Summary by Payor", "category", "Billing"),
        Map.of("name", "overdue_bills_summary", "description", "IP Overdue Bills Summary", "category", "Billing")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        m.put("bill_raised_summary", DATE_RANGE_PARAMS);
        m.put("bill_cancelled_summary", DATE_RANGE_PARAMS);
        m.put("discount_summary", DATE_RANGE_PARAMS);
        m.put("outstanding_bills_summary", DATE_RANGE_PARAMS);
        m.put("ip_outstanding_bills_summary", DATE_RANGE_PARAMS);

        m.put("unsettled_bills", List.of(
            param("from_date", "DATE", true,  "", "From Date"),
            param("to_date",   "DATE", true,  "", "To Date"),
            param("visit",     "VISIT", false, "ALL", "Encounter Mode")
        ));
        m.put("bills_raised_daywise", List.of(
            param("from_date", "DATE", true,  "", "From Date"),
            param("to_date",   "DATE", true,  "", "To Date"),
            param("visit",     "VISIT", false, "ALL", "Encounter Mode")
        ));
        m.put("bills_cancelled_daywise", List.of(
            param("from_date", "DATE", true,  "", "From Date"),
            param("to_date",   "DATE", true,  "", "To Date"),
            param("visit",     "VISIT", false, "ALL", "Encounter Mode")
        ));
        m.put("discount_report", List.of(
            param("from_date", "DATE", true,  "", "From Date"),
            param("to_date",   "DATE", true,  "", "To Date"),
            param("visit",     "VISIT", false, "ALL", "Encounter Mode")
        ));
        m.put("bills_overdue", List.of());
        m.put("overdue_bills_summary", List.of());
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
            case "bills_raised_daywise" -> billingReportDataService.getBillsRaisedDaywise(from, to, reportEngine.str(params, "visit"));
            case "bills_cancelled_daywise" -> billingReportDataService.getBillsCancelledDaywise(from, to, reportEngine.str(params, "visit"));
            case "discount_report" -> billingReportDataService.getDiscountReport(from, to, reportEngine.str(params, "visit"));
            case "bills_overdue" -> billingReportDataService.getBillsOverdue();
            case "unsettled_bills" -> billingReportDataService.getUnsettledBills(from, to, reportEngine.str(params, "visit"));
            case "bill_raised_summary" -> billingReportDataService.getBillRaisedSummary(from, to);
            case "bill_cancelled_summary" -> billingReportDataService.getBillCancelledSummary(from, to);
            case "discount_summary" -> billingReportDataService.getDiscountSummary(from, to);
            case "outstanding_bills_summary" -> billingReportDataService.getOutstandingBillsSummary(from, to);
            case "ip_outstanding_bills_summary" -> billingReportDataService.getIpOutstandingBillsSummary(from, to);
            case "overdue_bills_summary" -> billingReportDataService.getOverDueBillRaisedSummary();
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        if ("discount_report".equals(reportName)) {
            return buildDiscountReportHtml(rows, params);
        }
        if ("bills_overdue".equals(reportName)) {
            return buildOverdueReportHtml(rows, params);
        }
        return null;
    }

    private String buildDiscountReportHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
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

        String visit = reportEngine.str(params, "visit");
        if (visit == null || visit.trim().isEmpty()) {
            visit = "ALL";
        }
        
        String title = "Discount Report";
        String typeLabel = "Record";
        if ("OP".equalsIgnoreCase(visit)) {
            title = "OP- Discount Report";
            typeLabel = "OP";
        } else if ("IP".equalsIgnoreCase(visit)) {
            title = "IP- Discount Report";
            typeLabel = "IP";
        }

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='text-align: center; margin-bottom: 25px; border-bottom: 2px solid #e2e8f0; padding-bottom: 15px;'>");
        sb.append("<h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>").append(title).append("</h2>");
        if (rows.isEmpty()) {
            sb.append("<div style='font-size: 13px; color: #ef4444; font-weight: bold; margin-top: 6px;'>");
            sb.append("No ").append(typeLabel).append(" Record Found !!! There is no Discount from ").append(fromDate).append(" to ").append(toDate);
            sb.append("</div>");
        } else {
            sb.append("<div style='font-size: 13px; color: #64748b; margin-top: 6px;'>Discounts from ").append(fromDate).append(" to ").append(toDate).append("</div>");
        }
        sb.append("</div>");

        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Discount Date</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Bill No</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Patient No</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Patient</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Age/Sex</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Reason</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: right;'>Bill Amount</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: right;'>Discount Amount</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Given By</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody>");
 
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='9' style='padding: 20px; text-align: center; color: #94a3b8; font-style: italic;'>No records to display</td></tr>");
        } else {
            double totalBillAmt = 0;
            double totalDiscAmt = 0;
 
            for (java.util.Map<String, Object> r : rows) {
                sb.append("<tr>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.formatDateValue(r.get("discount_date")))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "bill_number"))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "patient_number"))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "patient_name"))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "age_sex"))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "reason"))).append("</td>");

                double billAmt = reportEngine.doubleVal(r.get("bill_amount"));
                double discAmt = reportEngine.doubleVal(r.get("discount_amount"));
                totalBillAmt += billAmt;
                totalDiscAmt += discAmt;

                sb.append("<td style='padding: 6px 10px; text-align: right;'>").append(reportEngine.formatGeneralValue(billAmt)).append("</td>");
                sb.append("<td style='padding: 6px 10px; text-align: right;'>").append(reportEngine.formatGeneralValue(discAmt)).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "given_by"))).append("</td>");
                sb.append("</tr>");
            }

            // Total Row
            sb.append("<tr>");
            sb.append("<td colspan='6' style='padding: 8px 10px; text-align: right; font-weight: bold;'>Total : Rs.</td>");
            sb.append("<td style='padding: 8px 10px; text-align: right; font-weight: bold;'>").append(reportEngine.formatGeneralValue(totalBillAmt)).append("</td>");
            sb.append("<td style='padding: 8px 10px; text-align: right; font-weight: bold;'>").append(reportEngine.formatGeneralValue(totalDiscAmt)).append("</td>");
            sb.append("<td style='padding: 8px 10px;'></td>");
            sb.append("</tr>");
        }

        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private String buildOverdueReportHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        String nowStr = "";
        try {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
            nowStr = now.format(dtf);
        } catch (Exception e) {
            nowStr = "";
        }

        sb.append("<div style='font-family:sans-serif;'>");
        sb.append("<div style='text-align: center; margin-bottom: 25px; border-bottom: 2px solid #e2e8f0; padding-bottom: 15px;'>");
        sb.append("<h2 style='font-size: 20px; font-weight: bold; margin: 0; color: #0f172a;'>IP OverDue Bills Report</h2>");
        sb.append("<div style='font-size: 13px; color: #64748b; margin-top: 6px;'>IP OverDue Bills on ").append(nowStr).append("</div>");
        sb.append("</div>");

        sb.append("<table>");
        sb.append("<thead><tr>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Bill Date</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Admission Date</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Bed No</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Patient No</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Patient</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: left;'>Age/Sex</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: right;'>Bill Amount</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: right;'>Net Amount</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: right;'>Paid</th>");
        sb.append("<th style='padding: 8px 10px; font-weight: 600; text-align: right;'>Due Amount</th>");
        sb.append("</tr></thead>");
        sb.append("<tbody>");
 
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='10' style='padding: 20px; text-align: center; color: #94a3b8; font-style: italic;'>No overdue bills found</td></tr>");
        } else {
            double totalBill = 0;
            double totalNet = 0;
            double totalPaid = 0;
            double totalDue = 0;
 
            for (java.util.Map<String, Object> r : rows) {
                sb.append("<tr>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.formatDateValue(r.get("bill_date")))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.formatDateValue(r.get("admission_date")))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "bed_no"))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "patient_no"))).append("</td>");
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(reportEngine.str(r, "patient"))).append("</td>");

                // Age/Sex combination
                String age = reportEngine.str(r, "Age");
                String sex = reportEngine.str(r, "Sex");
                String genderAbbr = "";
                if ("Male".equalsIgnoreCase(sex)) {
                    genderAbbr = "M";
                } else if ("Female".equalsIgnoreCase(sex)) {
                    genderAbbr = "F";
                } else if (sex != null && !sex.isEmpty()) {
                    genderAbbr = sex.substring(0, 1);
                }
                String ageSex = (age != null ? age : "") + (genderAbbr.isEmpty() ? "" : "/" + genderAbbr);
                sb.append("<td style='padding: 6px 10px;'>").append(reportEngine.escHtml(ageSex)).append("</td>");

                double billAmt = reportEngine.doubleVal(r.get("bill_amount"));
                double netAmt = reportEngine.doubleVal(r.get("net_amount"));
                double paid = reportEngine.doubleVal(r.get("paid"));
                double dueAmt = reportEngine.doubleVal(r.get("due_amount"));

                totalBill += billAmt;
                totalNet += netAmt;
                totalPaid += paid;
                totalDue += dueAmt;

                sb.append("<td style='padding: 6px 10px; text-align: right;'>").append(reportEngine.formatGeneralValue(billAmt)).append("</td>");
                sb.append("<td style='padding: 6px 10px; text-align: right;'>").append(reportEngine.formatGeneralValue(netAmt)).append("</td>");
                sb.append("<td style='padding: 6px 10px; text-align: right;'>").append(paid == 0 ? "-" : reportEngine.formatGeneralValue(paid)).append("</td>");
                sb.append("<td style='padding: 6px 10px; text-align: right;'>").append(reportEngine.formatGeneralValue(dueAmt)).append("</td>");
                sb.append("</tr>");
            }

            // Total Row
            sb.append("<tr>");
            sb.append("<td colspan='6' style='padding: 8px 10px; text-align: right; font-weight: bold;'>Total : Rs.</td>");
            sb.append("<td style='padding: 8px 10px; text-align: right; font-weight: bold;'>").append(reportEngine.formatGeneralValue(totalBill)).append("</td>");
            sb.append("<td style='padding: 8px 10px; text-align: right; font-weight: bold;'>").append(reportEngine.formatGeneralValue(totalNet)).append("</td>");
            sb.append("<td style='padding: 8px 10px; text-align: right; font-weight: bold;'>").append(totalPaid == 0 ? "-" : reportEngine.formatGeneralValue(totalPaid)).append("</td>");
            sb.append("<td style='padding: 8px 10px; text-align: right; font-weight: bold;'>").append(reportEngine.formatGeneralValue(totalDue)).append("</td>");
            sb.append("</tr>");
        }

        sb.append("</tbody></table></div>");
        return sb.toString();
    }
}
