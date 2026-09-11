package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * GST reports — outward supplies (pharmacy and OP/IP services), inward purchases, and
 * the two summaries built over them.
 *
 * <p>The Excel column headers below are the deliverable, not an implementation detail:
 * they reproduce the reference templates shared by the hospital, in order, so the
 * downloaded file drops into whatever already consumes it.
 *
 * <p>Two corrections are applied to the purchase template, which shipped with its
 * columns mis-mapped: its "TAX RATE" held tax <em>amounts</em>, "PURCHASE TAX" was
 * empty, and "NET VALUE" duplicated the gross. Here the rate is a rate, the tax is the
 * tax, and {@code PURCHASE VALUE + PURCHASE TAX = NET VALUE}.
 */
@Service
public class GstReportService extends BaseReportService {

    private final GstReportDataService gstReportDataService;

    public GstReportService(ReportEngine reportEngine, GstReportDataService gstReportDataService) {
        super(reportEngine);
        this.gstReportDataService = gstReportDataService;
    }

    static final String PHARMACY_SALES_GST = "pharmacy_sales_gst_detailed";
    static final String PURCHASE_GST       = "purchase_gst_details";
    static final String SERVICE_GST        = "service_gst_detailed";
    static final String HSN_TAX_SUMMARY    = "hsn_tax_summary";
    static final String GST_TAX_LIABILITY  = "gst_tax_liability";

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", PHARMACY_SALES_GST, "description", "Pharmacy Sales GST Detailed", "category", "GST"),
        Map.of("name", PURCHASE_GST,       "description", "Purchase GST Details",        "category", "GST"),
        Map.of("name", SERVICE_GST,        "description", "OP/IP Services GST Detailed", "category", "GST"),
        Map.of("name", HSN_TAX_SUMMARY,    "description", "HSN-wise Tax Summary",        "category", "GST"),
        Map.of("name", GST_TAX_LIABILITY,  "description", "Tax Liability Overview",      "category", "GST")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        m.put(PHARMACY_SALES_GST, List.of(
            param("from_date", "DATE",   true,  "", "From date"),
            param("to_date",   "DATE",   true,  "", "To date"),
            param("hsn_code",  "STRING", false, "", "HSN code")
        ));
        m.put(PURCHASE_GST, List.of(
            param("from_date",   "DATE",     true,  "", "From date"),
            param("to_date",     "DATE",     true,  "", "To date"),
            param("supplier_id", "SUPPLIER", false, "", "Supplier")
        ));
        m.put(SERVICE_GST, List.of(
            param("from_date",      "DATE",   true,  "", "From date"),
            param("to_date",        "DATE",   true,  "", "To date"),
            param("encounter_type", "STRING", false, "", "OP or IP (blank for both)")
        ));
        m.put(HSN_TAX_SUMMARY,   DATE_RANGE_PARAMS);
        m.put(GST_TAX_LIABILITY, DATE_RANGE_PARAMS);
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
            case PHARMACY_SALES_GST -> gstReportDataService.getPharmacySalesGstDetailed(
                from, to, reportEngine.str(params, "hsn_code"));
            case PURCHASE_GST -> gstReportDataService.getPurchaseGstDetails(
                from, to, reportEngine.str(params, "supplier_id"));
            case SERVICE_GST -> gstReportDataService.getServiceGstDetailed(
                from, to, reportEngine.str(params, "encounter_type"));
            case HSN_TAX_SUMMARY   -> gstReportDataService.getHsnTaxSummary(from, to);
            case GST_TAX_LIABILITY -> gstReportDataService.getGstTaxLiability(from, to);
            default -> List.of();
        };
    }

    /**
     * Maps query rows onto the reference templates' exact columns, in order.
     *
     * <p>{@code buildXlsx} takes its headers from the key order of each row map, so the
     * LinkedHashMaps built here <em>are</em> the sheet layout. SNo is generated during
     * this pass rather than in SQL — it numbers the exported rows, so it has to follow
     * the export's own ordering.
     */
    @Override
    protected List<Map<String, Object>> getExportRows(String reportName, List<Map<String, Object>> rows, Map<String, Object> params) {
        List<String> columns = switch (reportName) {
            case PHARMACY_SALES_GST -> PHARMACY_SALES_COLUMNS;
            case PURCHASE_GST       -> PURCHASE_COLUMNS;
            case SERVICE_GST        -> SERVICE_COLUMNS;
            case HSN_TAX_SUMMARY    -> HSN_SUMMARY_COLUMNS;
            case GST_TAX_LIABILITY  -> TAX_LIABILITY_COLUMNS;
            default                 -> null;
        };
        if (columns == null) return super.getExportRows(reportName, rows, params);

        // No data: hand back one marker row carrying the template's column names. The
        // engine skips its cells but still writes the header, so an empty period
        // downloads as a valid sheet with the right columns rather than a bare title.
        if (isEmpty(rows)) return List.of(emptyRow(columns));

        return switch (reportName) {
            case PHARMACY_SALES_GST -> buildPharmacySalesExportRows(rows);
            case PURCHASE_GST       -> buildPurchaseExportRows(rows);
            case SERVICE_GST        -> buildServiceExportRows(rows);
            case HSN_TAX_SUMMARY    -> buildHsnSummaryExportRows(rows);
            default                 -> buildTaxLiabilityExportRows(rows);
        };
    }

    /**
     * {@code ReportDbUtil.queryForList} never returns an empty list — a query with no
     * matches comes back as a single all-null row flagged {@code __EMPTY_ROW__}.
     */
    private boolean isEmpty(List<Map<String, Object>> rows) {
        return rows == null || rows.isEmpty()
            || (rows.size() == 1 && Boolean.TRUE.equals(rows.get(0).get("__EMPTY_ROW__")));
    }

    private Map<String, Object> emptyRow(List<String> columns) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String c : columns) row.put(c, null);
        row.put("__EMPTY_ROW__", true);
        return row;
    }

    private static final List<String> PHARMACY_SALES_COLUMNS = List.of(
        "SNo", "BILL DATE", "PATIENT NAME", "PATIENT ID", "BILL NO", "ITEM NAME", "HSN CODE",
        "UNITS", "MRP", "DISCOUNT", "TOTAL SALES VALUE", "TOTAL PURCHASE VALUE", "TAX RATE",
        "SALE (EXCLUDING TAX)", "GST", "SGST", "CGST");

    private static final List<String> PURCHASE_COLUMNS = List.of(
        "SNo", "SUPPLIER NAME", "GRN DATE", "GRN NO", "INVOICE NO", "INVOICE DATE",
        "TAX RATE", "PURCHASE VALUE", "PURCHASE TAX", "NET VALUE");

    private static final List<String> SERVICE_COLUMNS = List.of(
        "SNo", "BILL DATE", "TYPE", "PATIENT NAME", "PATIENT ID", "BILL NO", "SERVICE NAME",
        "SAC CODE", "GST TREATMENT", "UNITS", "RATE", "DISCOUNT", "TOTAL VALUE", "TAX RATE",
        "VALUE (EXCLUDING TAX)", "GST", "SGST", "CGST");

    private static final List<String> HSN_SUMMARY_COLUMNS = List.of(
        "SNo", "HSN CODE", "TAX RATE", "ITEMS", "QUANTITY", "TAXABLE VALUE",
        "GST", "SGST", "CGST", "TOTAL VALUE");

    private static final List<String> TAX_LIABILITY_COLUMNS = List.of(
        "TAX RATE", "OUTPUT TAXABLE", "OUTPUT TAX", "INPUT TAXABLE", "INPUT TAX", "NET LIABILITY");

    private List<Map<String, Object>> buildPharmacySalesExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        int sno = 1;
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SNo",                    sno++);
            row.put("BILL DATE",              reportEngine.str(r, "bill_date"));
            row.put("PATIENT NAME",           reportEngine.str(r, "patient_name"));
            row.put("PATIENT ID",             reportEngine.str(r, "patient_id"));
            row.put("BILL NO",                reportEngine.str(r, "bill_no"));
            row.put("ITEM NAME",              reportEngine.str(r, "item_name"));
            row.put("HSN CODE",               reportEngine.str(r, "hsn_code"));
            row.put("UNITS",                  reportEngine.toInt(r.get("units")));
            row.put("MRP",                    reportEngine.doubleVal(r.get("mrp")));
            row.put("DISCOUNT",               reportEngine.doubleVal(r.get("discount")));
            row.put("TOTAL SALES VALUE",      reportEngine.doubleVal(r.get("total_sales_value")));
            row.put("TOTAL PURCHASE VALUE",   reportEngine.doubleVal(r.get("total_purchase_value")));
            row.put("TAX RATE",               reportEngine.doubleVal(r.get("tax_rate")));
            row.put("SALE (EXCLUDING TAX)",   reportEngine.doubleVal(r.get("sale_excluding_tax")));
            row.put("GST",                    reportEngine.doubleVal(r.get("gst")));
            row.put("SGST",                   reportEngine.doubleVal(r.get("sgst")));
            row.put("CGST",                   reportEngine.doubleVal(r.get("cgst")));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> buildServiceExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        int sno = 1;
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SNo",                    sno++);
            row.put("BILL DATE",              reportEngine.str(r, "bill_date"));
            row.put("TYPE",                   reportEngine.str(r, "encounter_type"));
            row.put("PATIENT NAME",           reportEngine.str(r, "patient_name"));
            row.put("PATIENT ID",             reportEngine.str(r, "patient_id"));
            row.put("BILL NO",                reportEngine.str(r, "bill_no"));
            row.put("SERVICE NAME",           reportEngine.str(r, "service_name"));
            row.put("SAC CODE",               reportEngine.str(r, "sac_code"));
            row.put("GST TREATMENT",          reportEngine.str(r, "gst_treatment"));
            row.put("UNITS",                  reportEngine.toInt(r.get("units")));
            row.put("RATE",                   reportEngine.doubleVal(r.get("rate")));
            row.put("DISCOUNT",               reportEngine.doubleVal(r.get("discount")));
            row.put("TOTAL VALUE",            reportEngine.doubleVal(r.get("total_value")));
            row.put("TAX RATE",               reportEngine.doubleVal(r.get("tax_rate")));
            row.put("VALUE (EXCLUDING TAX)",  reportEngine.doubleVal(r.get("value_excluding_tax")));
            row.put("GST",                    reportEngine.doubleVal(r.get("gst")));
            row.put("SGST",                   reportEngine.doubleVal(r.get("sgst")));
            row.put("CGST",                   reportEngine.doubleVal(r.get("cgst")));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> buildHsnSummaryExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        int sno = 1;
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SNo",           sno++);
            row.put("HSN CODE",      reportEngine.str(r, "hsn_code"));
            row.put("TAX RATE",      reportEngine.doubleVal(r.get("tax_rate")));
            row.put("ITEMS",         reportEngine.toInt(r.get("items")));
            row.put("QUANTITY",      reportEngine.toInt(r.get("quantity")));
            row.put("TAXABLE VALUE", reportEngine.doubleVal(r.get("taxable_value")));
            row.put("GST",           reportEngine.doubleVal(r.get("gst")));
            row.put("SGST",          reportEngine.doubleVal(r.get("sgst")));
            row.put("CGST",          reportEngine.doubleVal(r.get("cgst")));
            row.put("TOTAL VALUE",   reportEngine.doubleVal(r.get("total_value")));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> buildTaxLiabilityExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("TAX RATE",       reportEngine.doubleVal(r.get("tax_rate")));
            row.put("OUTPUT TAXABLE", reportEngine.doubleVal(r.get("output_taxable")));
            row.put("OUTPUT TAX",     reportEngine.doubleVal(r.get("output_tax")));
            row.put("INPUT TAXABLE",  reportEngine.doubleVal(r.get("input_taxable")));
            row.put("INPUT TAX",      reportEngine.doubleVal(r.get("input_tax")));
            row.put("NET LIABILITY",  reportEngine.doubleVal(r.get("net_liability")));
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> buildPurchaseExportRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        int sno = 1;
        for (Map<String, Object> r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SNo",            sno++);
            row.put("SUPPLIER NAME",  reportEngine.str(r, "supplier_name"));
            row.put("GRN DATE",       reportEngine.str(r, "grn_date"));
            row.put("GRN NO",         reportEngine.str(r, "grn_no"));
            row.put("INVOICE NO",     reportEngine.str(r, "invoice_no"));
            row.put("INVOICE DATE",   reportEngine.str(r, "invoice_date"));
            row.put("TAX RATE",       reportEngine.doubleVal(r.get("tax_rate")));
            row.put("PURCHASE VALUE", reportEngine.doubleVal(r.get("purchase_value")));
            row.put("PURCHASE TAX",   reportEngine.doubleVal(r.get("purchase_tax")));
            row.put("NET VALUE",      reportEngine.doubleVal(r.get("net_value")));
            out.add(row);
        }
        return out;
    }
}
