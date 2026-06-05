package com.hms.application.report.modules;

import com.hms.application.report.BaseReportService;
import com.hms.application.report.ReportEngine;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProcurementReportService extends BaseReportService {

    private final ProcurementReportDataService procurementReportDataService;

    public ProcurementReportService(ReportEngine reportEngine, ProcurementReportDataService procurementReportDataService) {
        super(reportEngine);
        this.procurementReportDataService = procurementReportDataService;
    }

    private static final List<Map<String, String>> CATALOGUE = List.of(
        Map.of("name", "purchase_orders_report", "description", "Purchase Order Summary Report", "category", "Procurement"),
        Map.of("name", "goods_received_report", "description", "Goods Received Summary Report", "category", "Procurement"),
        Map.of("name", "goods_returned_report", "description", "Goods Returned Summary Report", "category", "Procurement")
    );

    private static final Map<String, List<Map<String, Object>>> PARAMS;

    static {
        Map<String, List<Map<String, Object>>> m = new LinkedHashMap<>();
        
        List<Map<String, Object>> PO_PARAMS = new ArrayList<>(DATE_RANGE_PARAMS);
        PO_PARAMS.add(Map.of("name", "supplier_id", "description", "Supplier", "type", "SUPPLIER", "required", false));
        PO_PARAMS.add(Map.of("name", "report_view_type", "description", "Report", "type", "REPORT_VIEW_TYPE", "required", true, "defaultValue", "summary"));

        for (Map<String, String> r : CATALOGUE) {
            if (r.get("name").equals("purchase_orders_report") || r.get("name").equals("goods_received_report") || r.get("name").equals("goods_returned_report")) {
                m.put(r.get("name"), PO_PARAMS);
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
            case "purchase_orders_report" -> {
                String viewType = params.getOrDefault("report_view_type", "summary").toString();
                yield procurementReportDataService.getPurchaseOrdersReport(from, to, viewType, params);
            }
            case "goods_received_report" -> {
                String viewType = params.getOrDefault("report_view_type", "summary").toString();
                yield procurementReportDataService.getGoodsReceivedReport(from, to, viewType, params);
            }
            case "goods_returned_report" -> {
                String viewType = params.getOrDefault("report_view_type", "summary").toString();
                yield procurementReportDataService.getGoodsReturnedReport(from, to, viewType, params);
            }
            default -> List.of();
        };
    }
}
