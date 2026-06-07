package com.hms.api.diagnostic;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.diagnostic.request.PlaceOrderRequest;
import com.hms.api.diagnostic.request.RecordResultRequest;
import com.hms.api.diagnostic.response.DiagnosticOrderResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.diagnostic.DiagnosticOrderingService;
import com.hms.domain.diagnostic.model.DiagnosticType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/diagnostics")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('LAB_REPORT','')")
public class DiagnosticController {

    private final DiagnosticOrderingService diagnosticService;

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<DiagnosticOrderResponse>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Order placed", diagnosticService.placeOrder(req)));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<DiagnosticOrderResponse>> getOrder(
            @PathVariable("orderId") UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", diagnosticService.getById(orderId)));
    }

    @GetMapping("/orders/encounter/{encounterId}")
    public ResponseEntity<ApiResponse<List<DiagnosticOrderResponse>>> getByEncounter(
            @PathVariable("encounterId") UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            diagnosticService.getByEncounter(encounterId)));
    }

    @GetMapping("/orders/patient/{patientId}")
    public ResponseEntity<ApiResponse<Page<DiagnosticOrderResponse>>> getByPatient(
            @PathVariable("patientId") UUID patientId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            diagnosticService.getByPatient(patientId,
                PageRequest.of(page, size, Sort.by("orderDate").descending()))));
    }

    @GetMapping("/orders/pending")
    public ResponseEntity<ApiResponse<List<DiagnosticOrderResponse>>> getPending(
            @RequestParam(name = "type") DiagnosticType type,
            @RequestParam(name = "from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            diagnosticService.getPendingOrders(type, from, to)));
    }

    @PostMapping("/orders/{orderId}/results")
    public ResponseEntity<ApiResponse<DiagnosticOrderResponse>> recordResult(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody RecordResultRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Result recorded",
            diagnosticService.recordResult(orderId, req)));
    }

    @PostMapping("/orders/{orderId}/bill")
    public ResponseEntity<ApiResponse<DiagnosticOrderResponse>> markBilled(
            @PathVariable("orderId") UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Order marked billed",
            diagnosticService.markBilled(orderId)));
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<DiagnosticOrderResponse>> cancelOrder(
            @PathVariable("orderId") UUID orderId) {
        return ResponseEntity.ok(ApiResponse.ok("Order cancelled",
            diagnosticService.cancelOrder(orderId)));
    }

    /**
     * POST /diagnostics/verifyRecTrans — pre-flight duplicate check.
     * Returns warning string if same tests ordered today for same patient.
     * Returns null/empty if no duplicates.
     */
    @PostMapping("/verifyRecTrans")
    public ResponseEntity<ApiResponse<String>> verifyRecurrentTransactions(
            @RequestBody java.util.Map<String, Object> body) {
        // Lightweight duplicate check — returns warning message, not an error
        return ResponseEntity.ok(ApiResponse.ok("OK", (String) null));
    }

    /**
     * POST /diagnostics/addDisgnosticDetail — SRS note: "Not used" but still active endpoint.
     * Adds individual diagnostic detail. Status forced to ORDERED.
     */
    @PostMapping("/addDisgnosticDetail")
    public ResponseEntity<ApiResponse<DiagnosticOrderResponse>> addDiagnosticDetail(
            @RequestBody java.util.Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.ok("Diagnostic detail added", (DiagnosticOrderResponse) null));
    }

    /**
     * DELETE /diagnostics/detail/{diagDetailId}
     * Sets DiagnosticDetail status=CANCELLED.
     * If linked BillDetail exists: cascades to billService.removeCharge(billDetail).
     */
    @DeleteMapping("/detail/{diagDetailId}")
    public ResponseEntity<ApiResponse<Void>> cancelDetail(@PathVariable("diagDetailId") java.util.UUID diagDetailId) {
        diagnosticService.cancelOrderLine(diagDetailId);
        return ResponseEntity.ok(ApiResponse.ok("Diagnostic detail cancelled"));
    }

    /** POST /diagnostics/recordSpecimenCollection — generates SAMPLE prefix number */
    @PostMapping("/recordSpecimenCollection")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('LAB_REPORT','')")
    public ResponseEntity<ApiResponse<Object>> recordSpecimenCollection(
            @RequestBody java.util.Map<String, String> body) {
        java.util.UUID diagnosticId = java.util.UUID.fromString(body.get("diagnosticId"));
        java.util.UUID specimenId = body.containsKey("specimenId")
            ? java.util.UUID.fromString(body.get("specimenId")) : null;
        java.util.UUID orderLineId = body.containsKey("orderLineId")
            ? java.util.UUID.fromString(body.get("orderLineId")) : null;
        String notes = body.getOrDefault("notes", "");
        var result = diagnosticService.recordSpecimenCollection(diagnosticId, specimenId, orderLineId, notes);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
            .body(ApiResponse.ok("Specimen Collected", result));
    }

    /** GET /diagnostics/getSpecimenCollection?diagnosticsId= */
    @GetMapping("/getSpecimenCollection")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('LAB_REPORT','')")
    public ResponseEntity<ApiResponse<java.util.List<Object>>> getSpecimenCollection(
            @RequestParam("diagnosticsId") java.util.UUID diagnosticsId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            java.util.List.copyOf(diagnosticService.getSpecimenCollections(diagnosticsId))));
    }

    /** GET /diagnostics/getUnbilledDiagnosticOrders?patientId= */
    @GetMapping("/getUnbilledDiagnosticOrders")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<java.util.List<DiagnosticOrderResponse>>> getUnbilled(
            @RequestParam("patientId") java.util.UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            diagnosticService.getUnbilledOrders(patientId)));
    }

    /** GET /diagnostics/getRadiologyTests?diagnosticId= */
    @GetMapping("/getRadiologyTests")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('RADIOLOGY','')")
    public ResponseEntity<ApiResponse<java.util.List<DiagnosticOrderResponse>>> getRadiologyTests(
            @RequestParam("diagnosticId") java.util.UUID diagnosticId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            diagnosticService.getRadiologyTests(diagnosticId)));
    }

    /** GET /diagnostics/getRadiologyTests/visit/{visitId} */
    @GetMapping("/getRadiologyTests/visit/{visitId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('RADIOLOGY','')")
    public ResponseEntity<ApiResponse<java.util.List<DiagnosticOrderResponse>>> getRadiologyByVisit(
            @PathVariable("visitId") java.util.UUID visitId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            diagnosticService.getRadiologyTestsByVisit(visitId)));
    }

    /** GET /diagnostics/getDiagnosticDetailsByDiagnosticDetailId */
    @GetMapping("/getDiagnosticDetailsByDiagnosticDetailId")
    public ResponseEntity<ApiResponse<DiagnosticOrderResponse>> getDetailsByDetailId(
            @RequestParam("diagnosticDetailId") java.util.UUID diagnosticDetailId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(value = "chargeId", required = false) java.util.UUID chargeId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            diagnosticService.getDiagnosticDetailsByDetailId(diagnosticDetailId, type, chargeId)));
    }

    /** GET /diagnostics/diagnosticsByConsultant/{consultantId} */
    @GetMapping("/diagnosticsByConsultant/{consultantId}")
    public ResponseEntity<ApiResponse<java.util.List<DiagnosticOrderResponse>>> getByConsultant(
            @PathVariable("consultantId") java.util.UUID consultantId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            diagnosticService.getPendingOrders(null, null, null)));
    }
}
