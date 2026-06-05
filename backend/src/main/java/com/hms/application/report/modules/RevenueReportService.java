package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RevenueReportService extends BaseReportService {

    private final RevenueReportDataService revenueReportDataService;

    public RevenueReportService(ReportEngine reportEngine, RevenueReportDataService revenueReportDataService) {
        super(reportEngine);
        this.revenueReportDataService = revenueReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "net_revenue_report", "description", "Net Revenue Report", "category", "Revenue"),
        Map.of("name", "consultant_revenue", "description", "Date/Consultant-wise Revenue Generated", "category", "Revenue"),
        Map.of("name", "department_revenue", "description", "Department-wise Revenue", "category", "Revenue"),
        Map.of("name", "room_revenue", "description", "RoomWise Bill Report", "category", "Revenue"),
        Map.of("name", "consultant_revenue_opip", "description", "Consultant Wise Revenue Report", "category", "Revenue"),
        Map.of("name", "department_revenue_opip", "description", "Category Wise Revenue", "category", "Revenue")
    );

    private static final List<Map<String, Object>> ROOM_REVENUE_PARAMS = List.of(
        param("from_date", "DATE", true,  "", "From Date"),
        param("to_date",   "DATE", true,  "", "To Date"),
        param("bed_type_id", "BED_TYPE", false, "", "Bed Type")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        for (Map<String, String> r : CATALOGUE) {
            if ("room_revenue".equals(r.get("name"))) {
                m.put(r.get("name"), ROOM_REVENUE_PARAMS);
            } else {
                m.put(r.get("name"), DATE_RANGE_PARAMS);
            }
        }
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
            case "net_revenue_report" -> revenueReportDataService.getBillsRaisedDaywise(from, to);
            case "consultant_revenue" -> revenueReportDataService.getConsultantRevenueReport(from, to);
            case "department_revenue" -> revenueReportDataService.getDepartmentRevenueReport(from, to);
            case "room_revenue" -> {
                UUID bedTypeId = reportEngine.uuid(params, "bed_type_id");
                yield revenueReportDataService.getRoomRevenueReport(from, to, bedTypeId);
            }
            case "consultant_revenue_opip" -> revenueReportDataService.getConsultantRevenueOPIP(from, to);
            case "department_revenue_opip" -> revenueReportDataService.getDepartmentRevenueOPIP(from, to);
            default -> List.of();
        };
    }

    @Override
    protected String buildCustomHtml(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        return switch (reportName) {
            case "net_revenue_report" -> buildNetRevenueHtml(rows, params);
            case "consultant_revenue_opip" -> buildConsultantRevenueOpIpHtml(rows, params);
            case "department_revenue_opip" -> buildDepartmentRevenueOpIpHtml(rows, params);
            case "room_revenue" -> buildRoomRevenueHtml(rows, params);
            default -> super.buildCustomHtml(reportName, rows, params);
        };
    }

    private String buildConsultantRevenueOpIpHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        String dateStr;
        if (from != null && from.equals(to)) {
            dateStr = "Consultant Wise Revenue on " + formatDate(from);
        } else {
            dateStr = "Consultant Wise Revenue from " + formatDate(from) + " to " + formatDate(to);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary'><strong>Consultant Wise Revenue Report</strong> &nbsp;|&nbsp; ")
          .append(dateStr)
          .append("</div>");

        sb.append("<table><thead><tr style='background-color: #1e40af; color: #ffffff;'>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Consultant</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>OP Bills</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>IP Bills</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Total</th>")
          .append("</tr></thead><tbody>");

        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='4' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            double totalOp = 0;
            double totalIp = 0;
            double totalSum = 0;

            for (Map<String, Object> row : rows) {
                double op = reportEngine.toDouble(row.get("op_bills"));
                double ip = reportEngine.toDouble(row.get("ip_bills"));
                double tot = reportEngine.toDouble(row.get("total"));

                totalOp += op;
                totalIp += ip;
                totalSum += tot;

                sb.append("<tr>")
                  .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "consultant_name"))).append("</td>")
                  .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(op)).append("</td>")
                  .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(ip)).append("</td>")
                  .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(tot)).append("</td>")
                  .append("</tr>");
            }

            // Totals row
            sb.append("<tr style='font-weight:bold;background:#e8f0fe;'>")
              .append("<td>Total Amount</td>")
              .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(totalOp)).append("</td>")
              .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(totalIp)).append("</td>")
              .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(totalSum)).append("</td>")
              .append("</tr>");
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String buildDepartmentRevenueOpIpHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        String dateStr;
        if (from != null && from.equals(to)) {
            dateStr = "Category Wise on " + formatDate(from);
        } else {
            dateStr = "Category Wise from " + formatDate(from) + " to " + formatDate(to);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary'> &nbsp;&nbsp; ")
          .append(dateStr)
          .append("</div>");

        sb.append("<table><thead><tr style='background-color: #1e40af; color: #ffffff;'>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Category</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>OP Bills</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>IP Bills</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Total</th>")
          .append("</tr></thead><tbody>");

        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='4' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            double totalOp = 0;
            double totalIp = 0;
            double totalSum = 0;

            for (Map<String, Object> row : rows) {
                double op = reportEngine.toDouble(row.get("op_bills"));
                double ip = reportEngine.toDouble(row.get("ip_bills"));
                double tot = reportEngine.toDouble(row.get("total"));

                totalOp += op;
                totalIp += ip;
                totalSum += tot;

                sb.append("<tr>")
                  .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "department"))).append("</td>")
                  .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(op)).append("</td>")
                  .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(ip)).append("</td>")
                  .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(tot)).append("</td>")
                  .append("</tr>");
            }

            // Totals row
            sb.append("<tr style='font-weight:bold;background:#e8f0fe;'>")
              .append("<td>Total :</td>")
              .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(totalOp)).append("</td>")
              .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(totalIp)).append("</td>")
              .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(totalSum)).append("</td>")
              .append("</tr>");
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String buildRoomRevenueHtml(List<Map<String, Object>> rows, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        String dateStr;
        if (from != null && from.equals(to)) {
            dateStr = "Room wise bill report on " + formatDate(from);
        } else {
            dateStr = "Room wise bill report from " + formatDate(from) + " to " + formatDate(to);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary'><strong>RoomWise Bill Report</strong> &nbsp;|&nbsp; ")
          .append(dateStr)
          .append("</div>");

        sb.append("<table><thead><tr style='background-color: #1e40af; color: #ffffff;'>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Bed No</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Bill No</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Patient No</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Patient</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Primary Consultant</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Admission Date</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Bill Amount</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Paid Amount</th>")
          .append("</tr></thead><tbody>");

        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='8' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            for (Map<String, Object> row : rows) {
                double billAmt = reportEngine.toDouble(row.get("bill_amount"));
                double paidAmt = reportEngine.toDouble(row.get("paid_amount"));

                sb.append("<tr>")
                  .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "bed_no"))).append("</td>")
                  .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "bill_no"))).append("</td>")
                  .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "patient_id"))).append("</td>")
                  .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "patient_name"))).append("</td>")
                  .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "consultant_name"))).append("</td>")
                  .append("<td>").append(reportEngine.formatGeneralValue(row.get("admission_date"))).append("</td>")
                  .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(billAmt)).append("</td>")
                  .append("<td style='text-align:right'>").append(reportEngine.formatGeneralValue(paidAmt)).append("</td>")
                  .append("</tr>");
            }
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String buildNetRevenueHtml(List<Map<String, Object>> allBills, Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        // Fetch the cancelled bills summary separately
        List<Map<String, Object>> cancelledList = revenueReportDataService.getBillCancelledSummary(from, to);
        Map<String, Object> cancelled = cancelledList.isEmpty() ? Map.of() : cancelledList.get(0);

        // ── Aggregate per bill type from the detail rows ─────────────────
        double opAmt = 0, opDisc = 0;
        double ipCashAmt = 0, ipCashDisc = 0;
        double ipCreditAmt = 0, ipCreditDisc = 0;
        double opCan = reportEngine.toDouble(cancelled.get("OP_CAN_AMOUNT"));
        double ipCashCan = reportEngine.toDouble(cancelled.get("IP_CASH_CAN_AMOUNT"));
        double ipCreditCan = reportEngine.toDouble(cancelled.get("IP_CREDIT_CAN_AMOUNT"));

        for (Map<String, Object> row : allBills) {
            int encType  = reportEngine.toInt(row.get("encounter_type"));
            int billType = reportEngine.toInt(row.get("bill_type"));
            double amt   = reportEngine.toDouble(row.get("bill_amount"));
            double disc  = reportEngine.toDouble(row.get("discount"));
            if (encType == 0) {
                opAmt += amt; opDisc += disc;
            } else if (encType == 1 && billType == 0) {
                ipCashAmt += amt; ipCashDisc += disc;
            } else if (encType == 1) {
                ipCreditAmt += amt; ipCreditDisc += disc;
            }
        }

        double opNet   = opAmt - opDisc;
        double ipCashNet = ipCashAmt - ipCashDisc;
        double ipCreditNet = ipCreditAmt - ipCreditDisc;
        double totalAmt   = opAmt + ipCashAmt + ipCreditAmt;
        double totalDisc  = opDisc + ipCashDisc + ipCreditDisc;
        double totalNet   = opNet + ipCashNet + ipCreditNet;
        double totalCan   = opCan + ipCashCan + ipCreditCan;
        double grandTotal = totalNet - totalCan;

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='summary'><strong>Net Revenue Report</strong> &nbsp;|&nbsp; ")
          .append("Net Revenue generated from ").append(formatDate(from)).append(" to ").append(formatDate(to))
          .append("</div>");

        // ── Revenue Summary table ────────────────────────────────────────
        sb.append("<strong style='font-size:13px;'>Revenue Summary</strong>");
        sb.append("<table><thead><tr style='background-color: #1e40af; color: #ffffff;'>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Bill Type</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Bill Amount</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Discount</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Net Amount</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Cancelled</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Total</th>")
          .append("</tr></thead><tbody>");

        sb.append(summaryRow("OP Bills",        opAmt,       opDisc,       opNet,       opCan,       opNet - opCan));
        sb.append(summaryRow("IP Cash Bills",   ipCashAmt,   ipCashDisc,   ipCashNet,   ipCashCan,   ipCashNet - ipCashCan));
        sb.append(summaryRow("IP Credit Bills", ipCreditAmt, ipCreditDisc, ipCreditNet, ipCreditCan, ipCreditNet - ipCreditCan));

        // Totals row
        sb.append("<tr style='font-weight:bold;background:#e8f0fe;'>")
          .append("<td>Total</td>")
          .append(td(totalAmt)).append(td(totalDisc)).append(td(totalNet)).append(td(totalCan)).append(td(grandTotal))
          .append("</tr>");
        sb.append("</tbody></table>");
        sb.append("<br/>");

        // ── Revenue Detail table ─────────────────────────────────────────
        sb.append("<strong style='font-size:13px;'>Revenue Detail</strong>");
        sb.append("<table><thead><tr style='background-color: #1e40af; color: #ffffff;'>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Bill No</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Bill Date</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Patient No</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Patient</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Bill Amount</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Discount</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold; text-align:right;'>Net Amount</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>User Name</th>")
          .append("<th style='padding: 8px 10px; font-weight: bold;'>Remark</th>")
          .append("</tr></thead><tbody>");

        // Group: OP Bills first, then IP Cash, then IP Credit
        java.util.function.Predicate<Map<String, Object>> isOp       = r -> reportEngine.toInt(r.get("encounter_type")) == 0;
        java.util.function.Predicate<Map<String, Object>> isIpCash   = r -> reportEngine.toInt(r.get("encounter_type")) == 1 && reportEngine.toInt(r.get("bill_type")) == 0;
        java.util.function.Predicate<Map<String, Object>> isIpCredit = r -> reportEngine.toInt(r.get("encounter_type")) == 1 && reportEngine.toInt(r.get("bill_type")) != 0;

        if (allBills.isEmpty()) {
            sb.append("<tr><td colspan='9' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            appendDetailSection(sb, "OP Bills", allBills.stream().filter(isOp).toList());
            appendDetailSection(sb, "IP Cash Bills", allBills.stream().filter(isIpCash).toList());
            appendDetailSection(sb, "IP Credit Bills", allBills.stream().filter(isIpCredit).toList());
        }

        sb.append("</tbody></table>");
        return sb.toString();
    }

    private String summaryRow(String label, double amt, double disc, double net, double can, double total) {
        return "<tr><td>" + reportEngine.escHtml(label) + "</td>" + td(amt) + td(disc) + td(net) + td(can) + td(total) + "</tr>";
    }

    private String td(double val) {
        return "<td style='text-align:right'>" + String.format("%.2f", val) + "</td>";
    }

    private void appendDetailSection(StringBuilder sb, String heading, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        sb.append("<tr><td colspan='9' style='padding:6px 10px;font-weight:bold;background:#f1f5f9;'>")
          .append(reportEngine.escHtml(heading)).append("</td></tr>");
        for (Map<String, Object> row : rows) {
            String remark = "";
            Object btObj = row.get("bill_type");
            if (btObj != null) {
                int bt = reportEngine.toInt(btObj);
                if (bt == 2) remark = "Cancelled";
            }
            sb.append("<tr>")
              .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "bill_number"))).append("</td>")
              .append("<td>").append(reportEngine.escHtml(reportEngine.formatDateValue(row.get("bill_date")))).append("</td>")
              .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "patient_number"))).append("</td>")
              .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "patient_name"))).append("</td>")
              .append("<td style='text-align:right'>").append(String.format("%.2f", reportEngine.toDouble(row.get("bill_amount")))).append("</td>")
              .append("<td style='text-align:right'>").append(String.format("%.2f", reportEngine.toDouble(row.get("discount")))).append("</td>")
              .append("<td style='text-align:right'>").append(String.format("%.2f", reportEngine.toDouble(row.get("net_amount")))).append("</td>")
              .append("<td>").append(reportEngine.escHtml(reportEngine.str(row, "raised_by"))).append("</td>")
              .append("<td>").append(reportEngine.escHtml(remark)).append("</td>")
              .append("</tr>");
        }
    }

    private static String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "";
        try {
            String[] p = isoDate.split("-");
            return p[2] + "-" + p[1] + "-" + p[0];
        } catch (Exception e) { return isoDate; }
    }
}
