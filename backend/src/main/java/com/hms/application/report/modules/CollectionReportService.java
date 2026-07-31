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

    @Override
    protected List<Map<String, Object>> getExportRows(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        return switch (reportName) {
            case "receipts_detail" -> buildReceiptsExportRows(rows);
            case "deposits_detail" -> buildDepositsExportRows(rows);
            case "refunds_detail" -> buildRefundsExportRows(rows);
            case "petty_cash_detail" -> buildPettyCashExportRows(rows);
            case "net_collection_detail" -> buildNetCollectionExportRows(rows, params);
            default -> super.getExportRows(reportName, rows, params);
        };
    }

    @Override
    public byte[] executeAsBinary(String reportName, Map<String, Object> params, String format) {
        if ("net_collection_detail".equals(reportName) && "XLSX".equals(format)) {
            return buildNetCollectionMultiSectionXlsx(params);
        }
        return super.executeAsBinary(reportName, params, format);
    }

    private byte[] buildNetCollectionMultiSectionXlsx(Map<String, Object> params) {
        String from = reportEngine.dateStr(params, "from_date");
        String to   = reportEngine.dateStr(params, "to_date");

        // Fetch all data sections (same as PDF HTML builder)
        List<Map<String, Object>> summaryRows = decryptQueryResult(ds.getNetCollectionSummary(from, to));
        List<Map<String, Object>> deposits  = decryptQueryResult(ds.getDepositsDetail(from, to, "ALL", "ALL", "ALL"));
        List<Map<String, Object>> refunds   = decryptQueryResult(ds.getRefundsDetail(from, to, "ALL", "ALL", "ALL"));
        List<Map<String, Object>> pettyCash = decryptQueryResult(ds.getPettyCashDetail(from, to));
        List<Map<String, Object>> discounts = decryptQueryResult(ds.getDiscountsDetail(from, to));

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {

            org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("Net Collection Report");

            // ── Styles ──
            org.apache.poi.xssf.usermodel.XSSFCellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            org.apache.poi.xssf.usermodel.XSSFCellStyle sectionStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont sectionFont = workbook.createFont();
            sectionFont.setBold(true); sectionFont.setFontHeightInPoints((short) 12);
            sectionStyle.setFont(sectionFont);

            org.apache.poi.xssf.usermodel.XSSFCellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true); headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            org.apache.poi.xssf.usermodel.XSSFCellStyle totalStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            totalStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            org.apache.poi.xssf.usermodel.XSSFCellStyle numStyle = workbook.createCellStyle();
            numStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);

            org.apache.poi.xssf.usermodel.XSSFCellStyle textStyle = workbook.createCellStyle();
            textStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);

            org.apache.poi.xssf.usermodel.XSSFCellStyle totalNumStyle = workbook.createCellStyle();
            totalNumStyle.setFont(totalFont);
            totalNumStyle.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT);
            totalNumStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            totalNumStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 0;

            // ── Hospital name & address ──
            String hospitalName = reportEngine.getHospitalName();
            org.apache.poi.xssf.usermodel.XSSFRow r0 = sheet.createRow(rowIdx++);
            org.apache.poi.xssf.usermodel.XSSFCell c0 = r0.createCell(0);
            c0.setCellValue(hospitalName); c0.setCellStyle(titleStyle);

            String hospitalAddr = reportEngine.getHospitalAddress();
            if (hospitalAddr != null && !hospitalAddr.isEmpty()) {
                sheet.createRow(rowIdx++).createCell(0).setCellValue(hospitalAddr);
            }
            rowIdx++; // blank

            // ── Report title & date range ──
            org.apache.poi.xssf.usermodel.XSSFRow titleRow = sheet.createRow(rowIdx++);
            org.apache.poi.xssf.usermodel.XSSFCell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Net Collection Report"); titleCell.setCellStyle(sectionStyle);

            String criteria = reportEngine.formatSearchCriteria(params);
            if (!criteria.isEmpty()) {
                sheet.createRow(rowIdx++).createCell(0).setCellValue(criteria);
            }
            rowIdx++; // blank

            String userParam = reportEngine.str(params, "user");
            boolean singleUser = !userParam.isEmpty() && !"ALL".equals(userParam);

            if (!singleUser) {
                // ══════════════════════════════════════════════════════════════════
                // SECTION 1: Collection Summary (All Users)
                // ══════════════════════════════════════════════════════════════════
                org.apache.poi.xssf.usermodel.XSSFRow secRow1 = sheet.createRow(rowIdx++);
            org.apache.poi.xssf.usermodel.XSSFCell secCell1 = secRow1.createCell(0);
            secCell1.setCellValue("Collection Summary"); secCell1.setCellStyle(sectionStyle);

            String[] summaryHeaders = {"User", "Cash", "Petty Cash", "Cash In Hand", "Card", "UPI", "Net"};
            org.apache.poi.xssf.usermodel.XSSFRow hdr1 = sheet.createRow(rowIdx++);
            for (int i = 0; i < summaryHeaders.length; i++) {
                org.apache.poi.xssf.usermodel.XSSFCell cell = hdr1.createCell(i);
                cell.setCellValue(summaryHeaders[i]); cell.setCellStyle(headerStyle);
            }

            double tCash=0, tPetty=0, tHand=0, tCard=0, tUpi=0, tNet=0;
            for (Map<String, Object> sr : summaryRows) {
                org.apache.poi.xssf.usermodel.XSSFRow dataRow = sheet.createRow(rowIdx++);
                double cash = reportEngine.doubleVal(sr.get("collection_cash"));
                double petty = reportEngine.doubleVal(sr.get("petty_cash"));
                double hand = reportEngine.doubleVal(sr.get("cash_in_hand"));
                double card = reportEngine.doubleVal(sr.get("card"));
                double upi  = reportEngine.doubleVal(sr.get("upi"));
                double net  = reportEngine.doubleVal(sr.get("net"));

                org.apache.poi.xssf.usermodel.XSSFCell uCell = dataRow.createCell(0);
                uCell.setCellValue(reportEngine.str(sr, "user")); uCell.setCellStyle(textStyle);

                dataRow.createCell(1).setCellValue(cash); dataRow.getCell(1).setCellStyle(numStyle);
                dataRow.createCell(2).setCellValue(petty); dataRow.getCell(2).setCellStyle(numStyle);
                dataRow.createCell(3).setCellValue(hand); dataRow.getCell(3).setCellStyle(numStyle);
                dataRow.createCell(4).setCellValue(card); dataRow.getCell(4).setCellStyle(numStyle);
                dataRow.createCell(5).setCellValue(upi); dataRow.getCell(5).setCellStyle(numStyle);
                dataRow.createCell(6).setCellValue(net); dataRow.getCell(6).setCellStyle(numStyle);
                tCash+=cash; tPetty+=petty; tHand+=hand; tCard+=card; tUpi+=upi; tNet+=net;
            }
            // Total row
            org.apache.poi.xssf.usermodel.XSSFRow totRow1 = sheet.createRow(rowIdx++);
            org.apache.poi.xssf.usermodel.XSSFCell c0Tot = totRow1.createCell(0);
            c0Tot.setCellValue("Total"); c0Tot.setCellStyle(totalStyle);

            double[] summaryTotalsArr = {tCash, tPetty, tHand, tCard, tUpi, tNet};
            for (int i = 0; i < summaryTotalsArr.length; i++) {
                org.apache.poi.xssf.usermodel.XSSFCell c = totRow1.createCell(i + 1);
                c.setCellValue(summaryTotalsArr[i]); c.setCellStyle(totalNumStyle);
            }

            rowIdx += 2; // blank rows between sections

            // ══════════════════════════════════════════════════════════════════
            // SECTION 2: Deposits (All Users Combined)
            // ══════════════════════════════════════════════════════════════════
            rowIdx = writeDetailSection(sheet, workbook, rowIdx, sectionStyle, headerStyle, totalStyle, numStyle, textStyle, totalNumStyle,
                "Deposits (All Users Combined)", deposits,
                new String[]{"deposit_no", "dpst_date", "patient_no", "patient", "deposit", "bill_date"},
                new String[]{"Deposit No", "Dpst Date", "Patient No", "Patient", "Deposit", "Bill Date"},
                new String[]{"deposit"});

            rowIdx += 2;

            // ══════════════════════════════════════════════════════════════════
            // SECTION 3: Refunds (All Users Combined)
            // ══════════════════════════════════════════════════════════════════
            rowIdx = writeDetailSection(sheet, workbook, rowIdx, sectionStyle, headerStyle, totalStyle, numStyle, textStyle, totalNumStyle,
                "Refunds (All Users Combined)", refunds,
                new String[]{"refund_no", "refund_date", "bill_no", "bill_date", "patient_no", "patient_name", "mode", "amount", "refund_reason"},
                new String[]{"Refund No", "Refund Date", "Bill No", "Bill Date", "Patient No", "Patient", "Mode", "Amount (Rs)", "Reason"},
                new String[]{"amount"});

            rowIdx += 2;

            // ══════════════════════════════════════════════════════════════════
            // SECTION 4: Petty Cash (All Users Combined)
            // ══════════════════════════════════════════════════════════════════
            rowIdx = writeDetailSection(sheet, workbook, rowIdx, sectionStyle, headerStyle, totalStyle, numStyle, textStyle, totalNumStyle,
                "Petty Cash (All Users Combined)", pettyCash,
                new String[]{"petty_cash_no", "date", "given_to", "mode", "remark", "amount"},
                new String[]{"Petty Cash No", "Date", "Paid To", "Mode", "Remark", "Amount (Rs)"},
                new String[]{"amount"});

            rowIdx += 2;

            // ══════════════════════════════════════════════════════════════════
            // SECTION 5: Discounts (All Users Combined)
            // ══════════════════════════════════════════════════════════════════
            rowIdx = writeDetailSection(sheet, workbook, rowIdx, sectionStyle, headerStyle, totalStyle, numStyle, textStyle, totalNumStyle,
                "Discounts (All Users Combined)", discounts,
                new String[]{"discount_date", "bill_no", "patient_no", "patient", "reason", "bill_amount", "discount", "net_amount"},
                new String[]{"Discount Date", "Bill No", "Patient No", "Patient", "Reason", "Bill Amount", "Discount Amount", "Net Amount"},
                new String[]{"bill_amount", "discount", "net_amount"});

            rowIdx += 2;

            // ══════════════════════════════════════════════════════════════════
            // SECTION 6: Summary Total (All Users Combined)
            // ══════════════════════════════════════════════════════════════════
            double totalDeposits = deposits.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("deposit"))).sum();
            double totalRefunds  = refunds.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
            double totalPettyCash = pettyCash.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
            double totalDiscounts = discounts.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("discount"))).sum();
            double netCollection = totalDeposits - totalRefunds - totalPettyCash;

            org.apache.poi.xssf.usermodel.XSSFRow secRow6 = sheet.createRow(rowIdx++);
            org.apache.poi.xssf.usermodel.XSSFCell secCell6 = secRow6.createCell(0);
            secCell6.setCellValue("Summary Total (All Users Combined)"); secCell6.setCellStyle(sectionStyle);

            String[] sumTotalHeaders = {"Type", "Amount (Rs)"};
            org.apache.poi.xssf.usermodel.XSSFRow hdr6 = sheet.createRow(rowIdx++);
            for (int i = 0; i < sumTotalHeaders.length; i++) {
                org.apache.poi.xssf.usermodel.XSSFCell cell = hdr6.createCell(i);
                cell.setCellValue(sumTotalHeaders[i]); cell.setCellStyle(headerStyle);
            }

            String[][] summaryTotalData = {
                {"Total Deposits", String.valueOf(totalDeposits)},
                {"Total Refunds", String.valueOf(totalRefunds)},
                {"Total Petty Cash", String.valueOf(totalPettyCash)},
                {"Total Discounts", String.valueOf(totalDiscounts)},
            };
            for (String[] pair : summaryTotalData) {
                org.apache.poi.xssf.usermodel.XSSFRow dr = sheet.createRow(rowIdx++);
                dr.createCell(0).setCellValue(pair[0]); dr.getCell(0).setCellStyle(textStyle);
                org.apache.poi.xssf.usermodel.XSSFCell vCell = dr.createCell(1);
                vCell.setCellValue(Double.parseDouble(pair[1])); vCell.setCellStyle(numStyle);
            }
                // Net Collection total row
                org.apache.poi.xssf.usermodel.XSSFRow netRow = sheet.createRow(rowIdx++);
                org.apache.poi.xssf.usermodel.XSSFCell netLabel = netRow.createCell(0);
                netLabel.setCellValue("Net Collection (Deposits - Refunds - Petty Cash)");
                netLabel.setCellStyle(totalStyle);
                org.apache.poi.xssf.usermodel.XSSFCell netVal = netRow.createCell(1);
                netVal.setCellValue(netCollection);
                netVal.setCellStyle(totalNumStyle);
                
                rowIdx += 2;
            }

            // ══════════════════════════════════════════════════════════════════
            // SECTION 7: Per-User Detailed Breakdown
            // ══════════════════════════════════════════════════════════════════
            Set<String> usernames = new java.util.LinkedHashSet<>();
            if (singleUser) {
                usernames.add(userParam);
            } else {
                for (Map<String, Object> r : summaryRows) {
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
                for (Map<String, Object> r : pettyCash) {
                    String u = reportEngine.str(r, "user");
                    if (!u.isEmpty()) usernames.add(u);
                }
                for (Map<String, Object> r : discounts) {
                    String u = reportEngine.str(r, "user");
                    if (!u.isEmpty()) usernames.add(u);
                }
            }

            for (String u : usernames) {
                rowIdx += 3;
                org.apache.poi.xssf.usermodel.XSSFRow uRow = sheet.createRow(rowIdx++);
                org.apache.poi.xssf.usermodel.XSSFCell uCell = uRow.createCell(0);
                uCell.setCellValue("User Net Collection Details - User: " + u);
                uCell.setCellStyle(sectionStyle);

                List<Map<String, Object>> uDeposits = deposits.stream().filter(r -> u.equals(r.get("user"))).toList();
                List<Map<String, Object>> uRefunds = refunds.stream().filter(r -> u.equals(r.get("user"))).toList();
                List<Map<String, Object>> uPettyCash = pettyCash.stream().filter(r -> u.equals(r.get("user"))).toList();
                List<Map<String, Object>> uDiscounts = discounts.stream().filter(r -> u.equals(r.get("user"))).toList();

                rowIdx = writeDetailSection(sheet, workbook, rowIdx, sectionStyle, headerStyle, totalStyle, numStyle, textStyle, totalNumStyle,
                    "Deposits", uDeposits,
                    new String[]{"deposit_no", "dpst_date", "patient_no", "patient", "deposit", "bill_date"},
                    new String[]{"Deposit No", "Dpst Date", "Patient No", "Patient", "Deposit", "Bill Date"},
                    new String[]{"deposit"});

                rowIdx += 1;
                rowIdx = writeDetailSection(sheet, workbook, rowIdx, sectionStyle, headerStyle, totalStyle, numStyle, textStyle, totalNumStyle,
                    "Refunds", uRefunds,
                    new String[]{"refund_no", "refund_date", "bill_no", "bill_date", "patient_no", "patient_name", "mode", "amount", "refund_reason"},
                    new String[]{"Refund No", "Refund Date", "Bill No", "Bill Date", "Patient No", "Patient", "Mode", "Amount (Rs)", "Reason"},
                    new String[]{"amount"});

                rowIdx += 1;
                rowIdx = writeDetailSection(sheet, workbook, rowIdx, sectionStyle, headerStyle, totalStyle, numStyle, textStyle, totalNumStyle,
                    "Petty Cash", uPettyCash,
                    new String[]{"petty_cash_no", "date", "given_to", "mode", "remark", "amount"},
                    new String[]{"Petty Cash No", "Date", "Paid To", "Mode", "Remark", "Amount (Rs)"},
                    new String[]{"amount"});

                rowIdx += 1;
                rowIdx = writeDetailSection(sheet, workbook, rowIdx, sectionStyle, headerStyle, totalStyle, numStyle, textStyle, totalNumStyle,
                    "Discounts", uDiscounts,
                    new String[]{"discount_date", "bill_no", "patient_no", "patient", "reason", "bill_amount", "discount", "net_amount"},
                    new String[]{"Discount Date", "Bill No", "Patient No", "Patient", "Reason", "Bill Amount", "Discount Amount", "Net Amount"},
                    new String[]{"bill_amount", "discount", "net_amount"});

                // User Summary Total
                rowIdx += 1;
                double uTotalDeposits = uDeposits.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("deposit"))).sum();
                double uTotalRefunds  = uRefunds.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
                double uTotalPettyCash = uPettyCash.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
                double uTotalDiscounts = uDiscounts.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("discount"))).sum();
                double uNetCollection = uTotalDeposits - uTotalRefunds - uTotalPettyCash;

                org.apache.poi.xssf.usermodel.XSSFRow uSumHeader = sheet.createRow(rowIdx++);
                uSumHeader.createCell(0).setCellValue("USER SUMMARY TOTAL");
                uSumHeader.getCell(0).setCellStyle(sectionStyle);

                org.apache.poi.xssf.usermodel.XSSFRow uHdr = sheet.createRow(rowIdx++);
                uHdr.createCell(0).setCellValue("Type"); uHdr.getCell(0).setCellStyle(headerStyle);
                uHdr.createCell(1).setCellValue("Amount (Rs)"); uHdr.getCell(1).setCellStyle(headerStyle);

                String[][] uSumData = {
                    {"Total Deposits", String.valueOf(uTotalDeposits)},
                    {"Total Refunds", String.valueOf(uTotalRefunds)},
                    {"Total Petty Cash", String.valueOf(uTotalPettyCash)},
                    {"Total Discounts", String.valueOf(uTotalDiscounts)},
                };
                for (String[] pair : uSumData) {
                    org.apache.poi.xssf.usermodel.XSSFRow dr = sheet.createRow(rowIdx++);
                    dr.createCell(0).setCellValue(pair[0]); dr.getCell(0).setCellStyle(textStyle);
                    org.apache.poi.xssf.usermodel.XSSFCell vCell = dr.createCell(1);
                    vCell.setCellValue(Double.parseDouble(pair[1])); vCell.setCellStyle(numStyle);
                }
                org.apache.poi.xssf.usermodel.XSSFRow uNetRow = sheet.createRow(rowIdx++);
                org.apache.poi.xssf.usermodel.XSSFCell uNetLbl = uNetRow.createCell(0);
                uNetLbl.setCellValue("Net Collection (Deposits - Refunds - Petty Cash)"); uNetLbl.setCellStyle(totalStyle);
                org.apache.poi.xssf.usermodel.XSSFCell uNetVal = uNetRow.createCell(1);
                uNetVal.setCellValue(uNetCollection); uNetVal.setCellStyle(totalNumStyle);
            }

            // Auto-size columns with min-width safety to avoid text/number touch
            for (int i = 0; i < 12; i++) {
                try {
                    sheet.autoSizeColumn(i);
                    int w = sheet.getColumnWidth(i);
                    sheet.setColumnWidth(i, Math.max(w + 1200, 4800));
                } catch (Exception e) {
                    sheet.setColumnWidth(i, 4800);
                }
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            throw new com.hms.exception.BusinessRuleViolationException("XLSX generation failed: " + ex.getMessage());
        }
    }

    /**
     * Writes a detail section into the Excel sheet with aligned numeric & text styling.
     */
    private int writeDetailSection(
            org.apache.poi.xssf.usermodel.XSSFSheet sheet,
            org.apache.poi.xssf.usermodel.XSSFWorkbook workbook,
            int rowIdx,
            org.apache.poi.xssf.usermodel.XSSFCellStyle sectionStyle,
            org.apache.poi.xssf.usermodel.XSSFCellStyle headerStyle,
            org.apache.poi.xssf.usermodel.XSSFCellStyle totalStyle,
            org.apache.poi.xssf.usermodel.XSSFCellStyle numStyle,
            org.apache.poi.xssf.usermodel.XSSFCellStyle textStyle,
            org.apache.poi.xssf.usermodel.XSSFCellStyle totalNumStyle,
            String sectionTitle,
            List<Map<String, Object>> rows,
            String[] keys,
            String[] headers,
            String[] totalKeys) {

        // Section title
        org.apache.poi.xssf.usermodel.XSSFRow secRow = sheet.createRow(rowIdx++);
        org.apache.poi.xssf.usermodel.XSSFCell secCell = secRow.createCell(0);
        secCell.setCellValue(sectionTitle); secCell.setCellStyle(sectionStyle);

        // Column headers
        org.apache.poi.xssf.usermodel.XSSFRow hdrRow = sheet.createRow(rowIdx++);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.xssf.usermodel.XSSFCell cell = hdrRow.createCell(i);
            cell.setCellValue(headers[i]); cell.setCellStyle(headerStyle);
        }

        // Identify total key indices
        Set<String> totalKeySet = new java.util.HashSet<>(java.util.Arrays.asList(totalKeys));
        double[] totals = new double[keys.length];

        if (rows == null || rows.isEmpty()) {
            org.apache.poi.xssf.usermodel.XSSFRow emptyRow = sheet.createRow(rowIdx++);
            emptyRow.createCell(0).setCellValue("No records"); emptyRow.getCell(0).setCellStyle(textStyle);
        } else {
            for (Map<String, Object> row : rows) {
                org.apache.poi.xssf.usermodel.XSSFRow dataRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < keys.length; i++) {
                    Object v = row.get(keys[i]);
                    org.apache.poi.xssf.usermodel.XSSFCell cell = dataRow.createCell(i);
                    if (totalKeySet.contains(keys[i])) {
                        double dv = reportEngine.doubleVal(v);
                        cell.setCellValue(dv); cell.setCellStyle(numStyle);
                        totals[i] += dv;
                    } else if (v instanceof java.sql.Date || v instanceof java.time.LocalDate) {
                        cell.setCellValue(reportEngine.formatDateValue(v)); cell.setCellStyle(textStyle);
                    } else {
                        cell.setCellValue(reportEngine.formatGeneralValue(v)); cell.setCellStyle(textStyle);
                    }
                }
            }

            // Total row
            if (totalKeys.length > 0) {
                org.apache.poi.xssf.usermodel.XSSFRow totRow = sheet.createRow(rowIdx++);
                int firstTotalIdx = -1;
                for (int i = 0; i < keys.length; i++) {
                    if (totalKeySet.contains(keys[i])) { firstTotalIdx = i; break; }
                }
                for (int i = 0; i < keys.length; i++) {
                    org.apache.poi.xssf.usermodel.XSSFCell cell = totRow.createCell(i);
                    if (totalKeySet.contains(keys[i])) {
                        cell.setCellValue(totals[i]); cell.setCellStyle(totalNumStyle);
                    } else if (i == (firstTotalIdx > 0 ? firstTotalIdx - 1 : 0)) {
                        cell.setCellValue("Total"); cell.setCellStyle(totalStyle);
                    } else {
                        cell.setCellStyle(totalStyle);
                    }
                }
            }
        }

        return rowIdx;
    }

    private List<Map<String, Object>> buildReceiptsExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> exportRows = new java.util.ArrayList<>();
        String[] keys = {"rcpt_date","receipt_no","bill_date","bill_no","patient_no","patient","age_sex","consultant","encounter_type","mode","amount","user"};
        String[] headers = {"Receipt Date","Receipt No","Bill Date","Bill No","Patient No","Patient","Age/Sex","Consultant","Encounter Type","Mode","Amount (Rs)","User"};
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object v = r.get(keys[i]);
                if ("amount".equals(keys[i])) {
                    row.put(headers[i], reportEngine.doubleVal(v));
                } else if (v instanceof java.sql.Date || v instanceof java.time.LocalDate) {
                    row.put(headers[i], reportEngine.formatDateValue(v));
                } else {
                    row.put(headers[i], reportEngine.formatGeneralValue(v));
                }
            }
            exportRows.add(row);
        }
        return exportRows;
    }

    private List<Map<String, Object>> buildDepositsExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> exportRows = new java.util.ArrayList<>();
        String[] keys = {"dpst_date","deposit_no","bill_date","adj_against_bill","patient_no","patient","age_sex","consultant","encounter_type","deposit","user"};
        String[] headers = {"Deposit Date","Deposit No","Bill Date","Bill No","Patient No","Patient","Age/Sex","Consultant","Encounter Type","Deposit","User"};
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object v = r.get(keys[i]);
                if ("deposit".equals(keys[i])) {
                    row.put(headers[i], reportEngine.doubleVal(v));
                } else if (v instanceof java.sql.Date || v instanceof java.time.LocalDate) {
                    row.put(headers[i], reportEngine.formatDateValue(v));
                } else {
                    row.put(headers[i], reportEngine.formatGeneralValue(v));
                }
            }
            exportRows.add(row);
        }
        return exportRows;
    }

    private List<Map<String, Object>> buildRefundsExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> exportRows = new java.util.ArrayList<>();
        String[] keys = {"refund_date","refund_no","bill_date","bill_no","patient_no","patient_name","age_sex","consultant","encounter_type","mode","refund_reason","amount","user"};
        String[] headers = {"Refund Date","Refund No","Bill Date","Bill No","Patient No","Patient","Age/Sex","Consultant","Encounter Type","Mode","Reason for Refund","Amount (Rs)","User"};
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object v = r.get(keys[i]);
                if ("amount".equals(keys[i])) {
                    row.put(headers[i], reportEngine.doubleVal(v));
                } else if (v instanceof java.sql.Date || v instanceof java.time.LocalDate) {
                    row.put(headers[i], reportEngine.formatDateValue(v));
                } else {
                    row.put(headers[i], reportEngine.formatGeneralValue(v));
                }
            }
            exportRows.add(row);
        }
        return exportRows;
    }

    private List<Map<String, Object>> buildPettyCashExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> exportRows = new java.util.ArrayList<>();
        String[] keys = {"petty_cash_no","date","given_to","mode","remark","amount","user"};
        String[] headers = {"Petty Cash No","Date","Paid To","Mode","Remark","Amount (Rs)","User"};
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                Object v = r.get(keys[i]);
                if ("amount".equals(keys[i])) {
                    row.put(headers[i], reportEngine.doubleVal(v));
                } else if (v instanceof java.sql.Date || v instanceof java.time.LocalDate) {
                    row.put(headers[i], reportEngine.formatDateValue(v));
                } else {
                    row.put(headers[i], reportEngine.formatGeneralValue(v));
                }
            }
            exportRows.add(row);
        }
        return exportRows;
    }

    private List<Map<String, Object>> buildNetCollectionExportRows(List<Map<String, Object>> summaryRows, Map<String, Object> params) {
        // Export the collection summary in same structure as PDF summary table
        List<Map<String, Object>> exportRows = new java.util.ArrayList<>();
        double tCash = 0, tPetty = 0, tHand = 0, tCard = 0, tUpi = 0, tNet = 0;
        for (Map<String, Object> r : summaryRows) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            double cash = reportEngine.doubleVal(r.get("collection_cash"));
            double petty = reportEngine.doubleVal(r.get("petty_cash"));
            double hand = reportEngine.doubleVal(r.get("cash_in_hand"));
            double card = reportEngine.doubleVal(r.get("card"));
            double upi = reportEngine.doubleVal(r.get("upi"));
            double net = reportEngine.doubleVal(r.get("net"));
            row.put("User", reportEngine.str(r, "user"));
            row.put("Cash", cash);
            row.put("Petty Cash", petty);
            row.put("Cash In Hand", hand);
            row.put("Card", card);
            row.put("UPI", upi);
            row.put("Net", net);
            exportRows.add(row);
            tCash += cash; tPetty += petty; tHand += hand; tCard += card; tUpi += upi; tNet += net;
        }
        // Add Total row to match PDF
        Map<String, Object> totalRow = new java.util.LinkedHashMap<>();
        totalRow.put("User", "Total");
        totalRow.put("Cash", tCash);
        totalRow.put("Petty Cash", tPetty);
        totalRow.put("Cash In Hand", tHand);
        totalRow.put("Card", tCard);
        totalRow.put("UPI", tUpi);
        totalRow.put("Net", tNet);
        exportRows.add(totalRow);
        return exportRows;
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
        List<Map<String, Object>> pettyCash = ds.getPettyCashDetail(from, to);

        // Find all unique usernames across all collections/payments/refunds/discounts/petty cash
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
        for (Map<String, Object> r : pettyCash) {
            String u = reportEngine.str(r, "user");
            if (!u.isEmpty()) usernames.add(u);
        }

        String userParam = reportEngine.str(params, "user");
        boolean singleUser = !userParam.isEmpty() && !"ALL".equals(userParam);

        if (singleUser) {
            usernames.clear();
            usernames.add(userParam);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:sans-serif;'>");

        if (!singleUser) {
            // ── Main Summary View Container ──
            sb.append("<div id='summary-view'>");
        sb.append("<div style='margin-bottom:20px;'>");
        sb.append("<div style='font-size:12px;color:#64748b;'>Net Collection from ").append(fromFmt).append(" to ").append(toFmt).append("</div>");
        sb.append("</div>");

        sb.append("<h3 style='font-size:14px;font-weight:bold;margin:16px 0 8px 0;'>Collection Summary</h3>");
        sb.append("<table><thead><tr>");
        sb.append("<th style='padding:8px 10px;text-align:left;' rowspan='2'>User</th>");
        sb.append("<th style='padding:8px 10px;text-align:center;' colspan='5'>Collection</th>");
        sb.append("<th style='padding:8px 10px;text-align:right;' rowspan='2'>Net</th>");
        sb.append("</tr><tr style='background:#525252;color:#fff;'>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#525252;color:#fff;'>Cash</th>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#525252;color:#fff;'>Petty Cash</th>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#525252;color:#fff;'>Cash In Hand</th>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#525252;color:#fff;'>Card</th>");
        sb.append("<th style='padding:6px 10px;text-align:right;background:#525252;color:#fff;'>UPI</th>");
        sb.append("</tr></thead><tbody>");

        double tCash=0, tPetty=0, tHand=0, tCard=0, tUpi=0, tNet=0;
        for (Map<String, Object> r : summaryRows) {
            String userVal = reportEngine.str(r, "user");
            double cash = reportEngine.doubleVal(r.get("collection_cash"));
            double petty = reportEngine.doubleVal(r.get("petty_cash"));
            double hand = reportEngine.doubleVal(r.get("cash_in_hand"));
            double card = reportEngine.doubleVal(r.get("card"));
            double upi  = reportEngine.doubleVal(r.get("upi"));
            double net = reportEngine.doubleVal(r.get("net"));
            tCash+=cash; tPetty+=petty; tHand+=hand; tCard+=card; tUpi+=upi; tNet+=net;

            sb.append("<tr>");
            sb.append("<td style='padding:6px 10px;text-align:left;'>");
            sb.append("<a href='#' class='summary-link' onclick=\"showUserDetail('").append(reportEngine.escHtml(userVal)).append("'); return false;\" style='color:#4b5563;text-decoration:none;font-weight:600;cursor:pointer;'>")
              .append(reportEngine.escHtml(userVal)).append("</a>");
            sb.append("</td>");
            tdN(sb, cash); tdN(sb, petty); tdN(sb, hand); tdN(sb, card); tdN(sb, upi); tdN(sb, net);
            sb.append("</tr>");
        }
        sb.append("<tr style='font-weight:bold;background:#f1f5f9;'>");
        td(sb, "Total", "left");
        tdN(sb, tCash); tdN(sb, tPetty); tdN(sb, tHand); tdN(sb, tCard); tdN(sb, tUpi); tdN(sb, tNet);
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

        sb.append("<div class='detail-table-title' style='font-size:14px;color:#525252;'>Petty Cash (All Users Combined)</div>");
        buildDetailTableWithTotal(sb, "table-petty-cash-combined", pettyCash, 
            new String[]{"petty_cash_no","date","given_to","mode","remark","amount"},
            new String[]{"Petty Cash No","Date","Paid To","Mode","Remark","Amount (Rs)"},
            "amount");

        sb.append("<div class='detail-table-title' style='font-size:14px;color:#525252;'>Discounts (All Users Combined)</div>");
        buildDetailTableWithTotal(sb, "table-discounts-combined", discounts, 
            new String[]{"discount_date","bill_no","patient_no","patient","reason","bill_amount","discount","net_amount"},
            new String[]{"Discount Date","Bill No","Patient No","Patient","Reason","Bill Amount","Discount Amount","Net Amount"},
            "bill_amount", "discount", "net_amount");

        // Combined Summary Total
        double totalDepositsCombined = deposits.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("deposit"))).sum();
        double totalRefundsCombined = refunds.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
        double totalPettyCashCombined = pettyCash.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
        double totalDiscountsCombined = discounts.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("discount"))).sum();
        double totalNetCombined = totalDepositsCombined - totalRefundsCombined - totalPettyCashCombined;

        sb.append("<div class='detail-table-title' style='font-size:14px;color:#525252;'>Summary Total (All Users Combined)</div>");
        sb.append("<table><thead><tr><th style='padding:8px 10px;text-align:left;'>Type</th><th style='padding:8px 10px;text-align:right;'>Amount (Rs)</th></tr></thead><tbody>");
        sb.append("<tr><td style='padding:6px 10px;'>Total Deposits</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalDepositsCombined)).append("</td></tr>");
        sb.append("<tr><td style='padding:6px 10px;'>Total Refunds</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalRefundsCombined)).append("</td></tr>");
        sb.append("<tr><td style='padding:6px 10px;'>Total Petty Cash</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalPettyCashCombined)).append("</td></tr>");
        sb.append("<tr><td style='padding:6px 10px;'>Total Discounts</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalDiscountsCombined)).append("</td></tr>");
        sb.append("<tr style='font-weight:bold;background:#f1f5f9;'><td style='padding:8px 10px;'>Net Collection (Deposits - Refunds - Petty Cash)</td><td style='padding:8px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(totalNetCombined)).append("</td></tr>");
        sb.append("</tbody></table>");

            sb.append("</div>"); // end summary-view
        }

        // ── Main Detail View Container ──
        if (singleUser) {
            sb.append("<div id='detail-view' style='display:block;'>");
        } else {
            sb.append("<div id='detail-view' style='display:none;'>");
        }
        sb.append("<div style='display:flex;align-items:center;justify-content:space-between;margin-bottom:15px;padding-bottom:8px;border-bottom:2px solid #e2e8f0;'>");
        sb.append("  <h3 style='font-size:15px;font-weight:bold;color:#0f172a;margin:0;'>User Net Collection Details - User: <span id='active-username' style='color:#525252;'>").append(singleUser ? reportEngine.escHtml(userParam) : "").append("</span></h3>");
        
        if (!singleUser) {
            sb.append("  <button onclick='goBackToSummary()' style='padding:6px 12px;background:#525252;color:#fff;border:none;border-radius:6px;cursor:pointer;font-weight:600;font-size:13px;display:inline-flex;align-items:center;gap:6px;box-shadow:0 1px 3px rgba(0,0,0,0.1);'><svg width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><line x1='19' y1='12' x2='5' y2='12'></line><polyline points='12 19 5 12 12 5'></polyline></svg>Back</button>");
        }
        sb.append("</div>");

        for (String user : usernames) {
            if (singleUser) {
                sb.append("<div id='details-").append(reportEngine.escHtml(user)).append("' class='user-details-section' style='display:block;'>");
            } else {
                sb.append("<div id='details-").append(reportEngine.escHtml(user)).append("' class='user-details-section' style='display:none;'>");
            }

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

            // User Petty Cash
            List<Map<String, Object>> userPettyCash = pettyCash.stream().filter(r -> user.equals(r.get("user"))).toList();
            double userPettyCashTotal = userPettyCash.stream().mapToDouble(r -> reportEngine.doubleVal(r.get("amount"))).sum();
            sb.append("<div class='detail-table-title'>Petty Cash</div>");
            buildDetailTableWithTotal(sb, "table-petty-cash-" + user, userPettyCash, 
                new String[]{"petty_cash_no","date","given_to","mode","remark","amount"},
                new String[]{"Petty Cash No","Date","Paid To","Mode","Remark","Amount (Rs)"},
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
            sb.append("<tr><td style='padding:6px 10px;'>Total Petty Cash</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(userPettyCashTotal)).append("</td></tr>");
            sb.append("<tr><td style='padding:6px 10px;'>Total Discounts</td><td style='padding:6px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(userDiscountsTotal)).append("</td></tr>");
            double userNet = userDepositsTotal - userRefundsTotal - userPettyCashTotal;
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;'><td style='padding:8px 10px;'>Net Collection (Deposits - Refunds - Petty Cash)</td><td style='padding:8px 10px;text-align:right;'>").append(reportEngine.formatGeneralValue(userNet)).append("</td></tr>");
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
        buildDetailTableWithGrandTotal(sb, rows, keys, headers, null);
    }

    private void buildDetailTableWithGrandTotal(StringBuilder sb, List<Map<String, Object>> rows, String[] keys, String[] headers, String totalKey) {
        sb.append("<table><thead><tr>");
        for (String h : headers) sb.append("<th style='padding:8px 10px;text-align:left;'>").append(h).append("</th>");
        sb.append("</tr></thead><tbody>");
        if (rows.isEmpty()) {
            sb.append("<tr><td colspan='").append(headers.length).append("' style='padding:12px;text-align:center;color:#94a3b8;font-style:italic;'>No records</td></tr>");
        } else {
            double grandTotal = 0;
            int totalKeyIdx = -1;
            if (totalKey != null) {
                for (int i = 0; i < keys.length; i++) {
                    if (keys[i].equals(totalKey)) {
                        totalKeyIdx = i;
                        break;
                    }
                }
            }

            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                for (String k : keys) {
                    Object v = r.get(k);
                    if (totalKey != null && k.equals(totalKey)) {
                        double doubleVal = reportEngine.doubleVal(v);
                        grandTotal += doubleVal;
                        tdN(sb, doubleVal);
                    } else {
                        String val = (v instanceof java.sql.Date || v instanceof java.time.LocalDate)
                                ? reportEngine.formatDateValue(v) : reportEngine.formatGeneralValue(v);
                        sb.append("<td style='padding:6px 10px;'>").append(reportEngine.escHtml(val)).append("</td>");
                    }
                }
                sb.append("</tr>");
            }

            if (totalKeyIdx >= 0) {
                sb.append("<tr style='font-weight:bold;background:#f1f5f9;' data-total-row='true'>");
                for (int i = 0; i < keys.length; i++) {
                    if (i == totalKeyIdx - 1) {
                        sb.append("<td style='padding:8px 10px;text-align:right;font-weight:bold;'>Grand Total</td>");
                    } else if (i == totalKeyIdx) {
                        sb.append("<td style='padding:8px 10px;text-align:right;font-weight:bold;'>").append(reportEngine.formatGeneralValue(grandTotal)).append("</td>");
                    } else {
                        sb.append("<td></td>");
                    }
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
        buildDetailTableWithGrandTotal(sb, rows, new String[]{"rcpt_date","receipt_no","bill_date","bill_no","patient_no","patient","age_sex","consultant","encounter_type","mode","amount","user"},
                new String[]{"Receipt Date","Receipt No","Bill Date","Bill No","Patient No","Patient","Age/Sex","Consultant","Encounter Type","Mode","Amount (Rs)","User"}, "amount");
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
        buildDetailTableWithGrandTotal(sb, rows, new String[]{"dpst_date","deposit_no","bill_date","adj_against_bill","patient_no","patient","age_sex","consultant","encounter_type","deposit","user"},
                new String[]{"Deposit Date","Deposit No","Bill Date","Bill No","Patient No","Patient","Age/Sex","Consultant","Encounter Type","Deposit","User"}, "deposit");
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
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;' data-total-row='true'>");
            sb.append("<td colspan='11' style='text-align:right;padding:6px 10px;font-weight:bold;'>Grand Total</td>");
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
            String[] keys = {"petty_cash_no","date","given_to","mode","remark","amount","user"};
            for (Map<String, Object> r : rows) {
                sb.append("<tr>");
                for (String k : keys) {
                    Object v = r.get(k);
                    if ("amount".equals(k)) {
                        totalAmount += reportEngine.doubleVal(v);
                        td(sb, reportEngine.formatGeneralValue(reportEngine.doubleVal(v)), "left");
                    } else {
                        String val = (v instanceof java.sql.Date || v instanceof java.time.LocalDate)
                            ? reportEngine.formatDateValue(v) : reportEngine.formatGeneralValue(v);
                        td(sb, val, "left");
                    }
                }
                sb.append("</tr>");
            }
            sb.append("<tr style='font-weight:bold;background:#f1f5f9;' data-total-row='true'>");
            sb.append("<td colspan='5' style='text-align:right;padding:6px 10px;font-weight:bold;'>Grand Total</td>");
            td(sb, reportEngine.formatGeneralValue(totalAmount), "left");
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
