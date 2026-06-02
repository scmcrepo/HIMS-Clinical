package com.hms.api.report;

import com.hms.api.shared.ApiResponse;
import com.hms.application.report.BaseReportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

public abstract class BaseReportController {

    protected final BaseReportService reportService;

    protected BaseReportController(BaseReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> listReports() {
        return ResponseEntity.ok(ApiResponse.ok("OK", reportService.getAvailableReports()));
    }

    @GetMapping("/info/{reportName}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReportInfo(
            @PathVariable("reportName") String reportName) {
        return ResponseEntity.ok(ApiResponse.ok("OK", reportService.getReportInfo(reportName)));
    }

    @PostMapping(value = "/{reportName}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> executeReport(
            @PathVariable("reportName") String reportName,
            @RequestParam(name = "format", defaultValue = "HTML") String format,
            @RequestBody(required = false) Map<String, Object> params) {

        String fmt = format.toUpperCase();

        if ("HTML".equals(fmt)) {
            String html = reportService.executeAsHtml(reportName, params != null ? params : Map.of());
            return ResponseEntity.ok(ApiResponse.ok("Report generated",
                Map.of("htmlContent", html, "reportName", reportName)));
        }

        if ("JSON".equals(fmt)) {
            List<Map<String, Object>> data = reportService.executeAsJson(reportName, params != null ? params : Map.of());
            return ResponseEntity.ok(ApiResponse.ok("Report data generated", data));
        }

        byte[] bytes = reportService.executeAsBinary(reportName,
            params != null ? params : Map.of(), fmt);

        MediaType contentType = switch (fmt) {
            case "PDF"  -> MediaType.APPLICATION_PDF;
            case "XLSX" -> MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "CSV"  -> MediaType.parseMediaType("text/csv");
            default     -> MediaType.APPLICATION_OCTET_STREAM;
        };

        String extension = fmt.toLowerCase();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename(reportName + "." + extension)
                .build());

        return ResponseEntity.ok()
            .headers(headers)
            .contentType(contentType)
            .body(new ByteArrayResource(bytes));
    }
}
