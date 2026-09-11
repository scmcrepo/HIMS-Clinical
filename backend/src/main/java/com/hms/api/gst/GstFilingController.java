package com.hms.api.gst;

import com.hms.api.shared.ApiResponse;
import com.hms.application.gst.GstFilingService;
import com.hms.domain.gst.model.GstFilingRecord;
import com.hms.domain.gst.model.GstReturnType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Generating, listing and downloading GST returns (A2–A6). */
@RestController
@RequestMapping("/gst/filing")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_GST','')")
public class GstFilingController {

    private final GstFilingService filingService;

    /** POST /gst/filing/generate?returnType=GSTR1&period=2026-09 */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generate(
            @RequestParam("returnType") GstReturnType returnType,
            @RequestParam("period") String period) {
        YearMonth ym = parsePeriod(period);
        GstFilingRecord record = filingService.generate(returnType, ym);
        return ResponseEntity.ok(ApiResponse.ok(
            record.getErrors().isEmpty()
                ? "Return generated"
                : record.getErrors().size() + " issue(s) found — see the details below",
            summarise(record)));
    }

    /** GET /gst/filing — most recent first. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            filingService.history().stream().map(this::summarise).toList()));
    }

    /** GET /gst/filing/{id} — one record, with its validation errors. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", summarise(filingService.get(id))));
    }

    /** GET /gst/filing/{id}/payload — A4, the file a hospital uploads to the GST portal. */
    @GetMapping("/{id}/payload")
    public ResponseEntity<ByteArrayResource> downloadPayload(@PathVariable("id") UUID id) {
        GstFilingRecord record = filingService.get(id);
        byte[] bytes = filingService.payloadOf(id).getBytes(StandardCharsets.UTF_8);

        String filename = record.getReturnType() + "_" + record.getReturnPeriod() + ".json";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ByteArrayResource(bytes));
    }

    /** POST /gst/filing/{id}/submit — A5. */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Return submitted", summarise(filingService.submit(id))));
    }

    private YearMonth parsePeriod(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Period must be in the form 2026-09 — got '" + period + "'");
        }
    }

    private Map<String, Object> summarise(GstFilingRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                r.getId());
        m.put("returnType",        r.getReturnType());
        m.put("returnPeriod",      r.getReturnPeriod());
        m.put("periodStart",       r.getPeriodStart());
        m.put("periodEnd",         r.getPeriodEnd());
        m.put("filingStatus",      r.getFilingStatus());
        m.put("environment",       r.getEnvironment());
        m.put("totalTaxableValue", r.getTotalTaxableValue());
        m.put("totalCgst",         r.getTotalCgst());
        m.put("totalSgst",         r.getTotalSgst());
        m.put("totalIgst",         r.getTotalIgst());
        m.put("lineItemCount",     r.getLineItemCount());
        m.put("referenceId",       r.getReferenceId());
        m.put("submittedAt",       r.getSubmittedAt());
        m.put("createdAt",         r.getCreatedAt());
        m.put("errors", r.getErrors().stream().map(e -> {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("code",          e.getErrorCode());
            em.put("field",         e.getFieldName());
            em.put("invoiceNumber", e.getInvoiceNumber());
            em.put("message",       e.getErrorMessage());
            return em;
        }).toList());
        return m;
    }
}
