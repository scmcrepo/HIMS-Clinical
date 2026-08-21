package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The insurance MIS report catalogue (WO-021 / IR-001).
 *
 * <p>Replaces the single generic "Insurance Claim Summary" card that routed to
 * the billing module and had no data service behind it.
 *
 * <p>Rendered through this repo's own {@link ReportEngine}, not JasperReports.
 * The source system's {@code .jrxml} files are its implementation of these ten
 * reports; the requirement is the reports. Adding a Jasper dependency and a
 * second rendering path to produce the same tables would leave the codebase with
 * two report engines to maintain forever.
 */
@Service
public class InsuranceReportService extends BaseReportService {

    private final InsuranceReportDataService ds;

    public InsuranceReportService(ReportEngine reportEngine, InsuranceReportDataService ds) {
        super(reportEngine);
        this.ds = ds;
    }

    private static final String CATEGORY = "Insurance";

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "preauth_raised",              "description", "Pre-Authorisation Raised",        "category", CATEGORY),
        Map.of("name", "preauth_status",              "description", "Pre-Authorisation Status",        "category", CATEGORY),
        Map.of("name", "enhancement_raised",          "description", "Enhancement Raised",              "category", CATEGORY),
        Map.of("name", "enhancement_status",          "description", "Enhancement Status",              "category", CATEGORY),
        Map.of("name", "claim_dispatch",              "description", "Claim Dispatch Report",           "category", CATEGORY),
        Map.of("name", "disallowance_summary",        "description", "Disallowance Summary",            "category", CATEGORY),
        Map.of("name", "disallowance_detail",         "description", "Disallowance Detail",             "category", CATEGORY),
        Map.of("name", "document_pending_status",     "description", "Document Pending Status",         "category", CATEGORY),
        Map.of("name", "ip_outstanding_credit_bills", "description", "IP Outstanding Credit Bills",     "category", CATEGORY),
        Map.of("name", "insurance_ageing_analysis",   "description", "Ageing Analysis",                 "category", CATEGORY)
    );

    private static final List<Map<String, Object>> DATE_PAYER_PARAMS = List.of(
        param("from_date", "DATE", true,  "",    "From Date"),
        param("to_date",   "DATE", true,  "",    "To Date"),
        param("payer",     "TEXT", false, "ALL", "Payer Type")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;
    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        m.put("preauth_raised",              DATE_PAYER_PARAMS);
        m.put("preauth_status",              DATE_PAYER_PARAMS);
        m.put("enhancement_raised",          DATE_PAYER_PARAMS);
        m.put("enhancement_status",          DATE_PAYER_PARAMS);
        m.put("claim_dispatch",              DATE_PAYER_PARAMS);
        m.put("disallowance_summary",        DATE_PAYER_PARAMS);
        m.put("disallowance_detail",         DATE_PAYER_PARAMS);
        m.put("document_pending_status",     DATE_PAYER_PARAMS);
        m.put("ip_outstanding_credit_bills", DATE_PAYER_PARAMS);
        m.put("insurance_ageing_analysis",   DATE_PAYER_PARAMS);
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
                info.put("category", meta.get("category"));
            });
        info.put("parameters", PARAMS.getOrDefault(reportName, List.of()));
        return info;
    }

    @Override
    public List<Map<String, Object>> executeDataQuery(String reportName, Map<String, Object> params) {
        String from  = reportEngine.dateStr(params, "from_date");
        String to    = reportEngine.dateStr(params, "to_date");
        String asOn  = asOnDate(params, to);
        String payer = orAll(reportEngine.str(params, "payer"));

        return switch (reportName) {
            case "preauth_raised"              -> ds.getPreAuthRaised(from, to, payer);
            case "preauth_status"              -> ds.getPreAuthStatus(from, to, payer);
            case "enhancement_raised"          -> ds.getEnhancementRaised(from, to, payer);
            case "enhancement_status"          -> ds.getEnhancementStatus(from, to, payer);
            case "claim_dispatch"              -> ds.getClaimDispatch(from, to, payer);
            case "disallowance_summary"        -> ds.getDisallowanceSummary(from, to, payer);
            case "disallowance_detail"         -> ds.getDisallowanceDetail(from, to, payer);
            case "document_pending_status"     -> ds.getDocumentPendingStatus(from, to, payer);
            case "ip_outstanding_credit_bills" -> ds.getOutstandingCreditBills(asOn, payer);
            case "insurance_ageing_analysis"   -> ds.getAgeingAnalysis(asOn, payer);
            default -> throw new com.hms.exception.BusinessRuleViolationException(
                "Unknown insurance report: " + reportName);
        };
    }

    private static String orAll(String value) {
        return (value == null || value.isEmpty()) ? "ALL" : value;
    }

    /**
     * The as-on date for the two point-in-time reports, falling back to
     * {@code to_date} and then to today.
     *
     * <p>The fallback exists because the shared {@code ReportCard} in the
     * frontend emits {@code from_date}/{@code to_date} for every report it
     * renders. Without this, the outstanding and ageing reports would receive no
     * as-on date, pass an empty string into {@code ?::DATE} and fail with a cast
     * error the moment anyone opened the card — a confusing failure for a
     * missing parameter the caller had no idea it owed us.
     *
     * <p>Falling back to the END of the range is the right choice: "outstanding
     * as on" a period means as at its close, not its start.
     */
    private String asOnDate(Map<String, Object> params, String toDate) {
        String asOn = reportEngine.dateStr(params, "as_on_date");
        if (asOn != null && !asOn.isBlank()) return asOn;
        if (toDate != null && !toDate.isBlank()) return toDate;
        return java.time.LocalDate.now().toString();
    }
}
