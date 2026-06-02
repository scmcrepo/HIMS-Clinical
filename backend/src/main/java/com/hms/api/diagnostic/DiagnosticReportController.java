package com.hms.api.diagnostic;

import com.hms.api.shared.ApiResponse;
import com.hms.application.diagnostic.DiagnosticReportService;
import com.hms.domain.diagnostic.model.DiagnosticReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/diagReport")
@RequiredArgsConstructor
public class DiagnosticReportController {

    private final DiagnosticReportService reportService;

    /** POST /diagReport — save lab reports (batch). Body: { orderLineId, templateId, reports: {ltdId: value} } */
    @PostMapping
    public ResponseEntity<ApiResponse<List<DiagnosticReport>>> saveLabReports(
            @RequestBody Map<String, Object> body) {
        UUID orderLineId = UUID.fromString((String) body.get("orderLineId"));
        UUID templateId = body.get("templateId") != null ? UUID.fromString((String) body.get("templateId")) : null;
        @SuppressWarnings("unchecked")
        Map<String, String> reports = (Map<String, String>) body.get("reports");
        return ResponseEntity.ok(ApiResponse.ok("Reports saved",
            reportService.saveLabReports(orderLineId, templateId, reports != null ? reports : Map.of())));
    }

    /** GET /diagReport?orderLineId= */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DiagnosticReport>>> getReports(
            @RequestParam(name = "orderLineId") UUID orderLineId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", reportService.getReportsByOrderLine(orderLineId)));
    }

    /** GET /diagReport/encounter/{encounterId} */
    @GetMapping("/encounter/{encounterId}")
    public ResponseEntity<ApiResponse<List<DiagnosticReport>>> getByEncounter(
            @PathVariable("encounterId") UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", reportService.getReportsByEncounter(encounterId)));
    }

    /** POST /diagReport/saveCustomReport */
    @PostMapping("/saveCustomReport")
    public ResponseEntity<ApiResponse<DiagnosticReport>> saveCustomReport(@RequestBody Map<String, String> body) {
        UUID orderLineId = UUID.fromString(body.get("orderLineId"));
        UUID templateId = UUID.fromString(body.get("templateId"));
        String templateData = body.get("templateData");
        return ResponseEntity.ok(ApiResponse.ok("Report saved",
            reportService.saveCustomReport(orderLineId, templateId, templateData)));
    }

    /** GET /diagReport/getCustomReport?orderLineId=&templateId= */
    @GetMapping("/getCustomReport")
    public ResponseEntity<ApiResponse<DiagnosticReport>> getCustomReport(
            @RequestParam(name = "orderLineId") UUID orderLineId, @RequestParam(name = "templateId") UUID templateId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", reportService.getCustomReport(orderLineId, templateId)));
    }
}
