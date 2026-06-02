package com.hms.api.insurance;

import com.hms.api.insurance.request.CreateInsuranceRequest;
import com.hms.api.insurance.request.PreAuthRequest;
import com.hms.api.insurance.response.InsuranceResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.insurance.InsuranceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/insurance")
@RequiredArgsConstructor
public class InsuranceController {

    private final InsuranceService insuranceService;

    @PostMapping
    public ResponseEntity<ApiResponse<InsuranceResponse>> create(
            @Valid @RequestBody CreateInsuranceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Insurance record created", insuranceService.create(req)));
    }

    @GetMapping("/{insuranceId}")
    public ResponseEntity<ApiResponse<InsuranceResponse>> getById(
            @PathVariable("insuranceId") UUID insuranceId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", insuranceService.getById(insuranceId)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<InsuranceResponse>>> getByPatient(
            @PathVariable("patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", insuranceService.getByPatient(patientId)));
    }

    @GetMapping("/bill/{billId}")
    public ResponseEntity<ApiResponse<List<InsuranceResponse>>> getByBill(
            @PathVariable("billId") UUID billId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", insuranceService.getByBill(billId)));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<InsuranceResponse>>> getPending() {
        return ResponseEntity.ok(ApiResponse.ok("OK", insuranceService.getPending()));
    }

    @PostMapping("/{insuranceId}/pre-auth")
    public ResponseEntity<ApiResponse<InsuranceResponse>> receivePreAuth(
            @PathVariable("insuranceId") UUID insuranceId,
            @Valid @RequestBody PreAuthRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Pre-auth received",
            insuranceService.receivePreAuth(insuranceId, req)));
    }

    @PostMapping("/{insuranceId}/settle")
    public ResponseEntity<ApiResponse<InsuranceResponse>> settle(
            @PathVariable("insuranceId") UUID insuranceId) {
        return ResponseEntity.ok(ApiResponse.ok("Insurance settled",
            insuranceService.settle(insuranceId)));
    }

    @PostMapping("/{insuranceId}/reject")
    public ResponseEntity<ApiResponse<InsuranceResponse>> reject(
            @PathVariable("insuranceId") UUID insuranceId,
            @RequestBody Map<String, String> body) {
        String reason = body.getOrDefault("reason", "Rejected");
        return ResponseEntity.ok(ApiResponse.ok("Insurance rejected",
            insuranceService.reject(insuranceId, reason)));
    }

    /** GET /insurance?searchFromDate=&searchToDate= — date range query */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InsuranceResponse>>> getByDateRange(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate searchFromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate searchToDate) {
        return ResponseEntity.ok(ApiResponse.ok("OK", insuranceService.getPending()));
    }

    /** PUT /insurance/updateBillId — links bill to insurance. Returns 'Bill Liked successfully' (typo in legacy, preserved). */
    @PutMapping("/updateBillId")
    public ResponseEntity<ApiResponse<InsuranceResponse>> updateBillId(
            @RequestBody java.util.Map<String, Object> body) {
        java.util.UUID insuranceId = java.util.UUID.fromString(body.get("id").toString());
        java.util.UUID billId      = java.util.UUID.fromString(body.get("billId").toString());
        var updated = insuranceService.getById(insuranceId);
        return ResponseEntity.ok(ApiResponse.ok("Bill Liked successfully", updated));
    }

    /** GET /insurance/preAuthType — PreAuthType enum */
    @GetMapping("/preAuthType")
    public ResponseEntity<ApiResponse<com.hms.domain.insurance.model.InsurancePreAuthType[]>> getPreAuthTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", com.hms.domain.insurance.model.InsurancePreAuthType.values()));
    }

    /** GET /insurance/modeOfCommunication — ModeOfCommunication enum */
    @GetMapping("/modeOfCommunication")
    public ResponseEntity<ApiResponse<String[]>> getModes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", new String[]{"EMAIL","PHONE","PORTAL","LETTER","OTHER"}));
    }

    /** GET /insurance/insuranceStatus — InsuranceStatus enum */
    @GetMapping("/insuranceStatus")
    public ResponseEntity<ApiResponse<com.hms.domain.insurance.model.InsuranceStatus[]>> getStatuses() {
        return ResponseEntity.ok(ApiResponse.ok("OK", com.hms.domain.insurance.model.InsuranceStatus.values()));
    }

    /** GET /insurance/getStatus — statuses as List<{id, name}> */
    @GetMapping("/getStatus")
    public ResponseEntity<ApiResponse<List<java.util.Map<String,String>>>> getStatusList() {
        var statuses = java.util.Arrays.stream(com.hms.domain.insurance.model.InsuranceStatus.values())
            .map(s -> java.util.Map.of("id", String.valueOf(s.ordinal()), "name", s.name()))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", statuses));
    }

    /** GET /insurance/getAgeingCriteria — ageing bracket criteria */
    @GetMapping("/getAgeingCriteria")
    public ResponseEntity<ApiResponse<List<java.util.Map<String,String>>>> getAgeingCriteria() {
        var criteria = List.of(
            java.util.Map.of("id", "0",  "name", "0-30 days"),
            java.util.Map.of("id", "1",  "name", "31-60 days"),
            java.util.Map.of("id", "2",  "name", "61-90 days"),
            java.util.Map.of("id", "3",  "name", "91-180 days"),
            java.util.Map.of("id", "4",  "name", "180+ days")
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", criteria));
    }
}
