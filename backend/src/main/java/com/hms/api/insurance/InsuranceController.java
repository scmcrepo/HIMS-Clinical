package com.hms.api.insurance;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.insurance.request.*;
import com.hms.api.insurance.response.InsuranceDeskResponse;
import com.hms.api.insurance.response.InsuranceResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.insurance.InsuranceDeskService;
import com.hms.application.insurance.InsuranceService;
import com.hms.domain.insurance.model.*;
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
@PreAuthorize("hasPermission('INSURANCE','')")
public class InsuranceController {

    private final InsuranceService insuranceService;

    /** The seven-stage manual TPA desk flow (WO-020). */
    private final InsuranceDeskService deskService;

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

    // ────────────────────────────────────────────────────────────────────────
    //  Manual TPA insurance desk — the seven progressive stages (WO-020).
    //
    //  Every stage is an upsert on the claim and is therefore idempotent:
    //  resubmitting a stage rewrites that stage's fields and re-stamps its
    //  updated_by/date. Progression is monotonic, so correcting an early stage
    //  after dispatch does not drag the claim backwards.
    // ────────────────────────────────────────────────────────────────────────

    /** Stage 1 — pre-authorisation request sent to the TPA. */
    @PostMapping("/{insuranceId}/stages/preauth")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> submitPreauth(
            @PathVariable("insuranceId") UUID insuranceId,
            @Valid @RequestBody PreauthStageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Pre-authorisation request saved",
            deskService.submitPreauth(insuranceId, req)));
    }

    /** Stage 2 — the TPA's decision on the pre-authorisation. */
    @PostMapping("/{insuranceId}/stages/preauth-approval")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> submitPreauthApproval(
            @PathVariable("insuranceId") UUID insuranceId,
            @Valid @RequestBody PreauthApprovalStageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Pre-authorisation decision saved",
            deskService.submitPreauthApproval(insuranceId, req)));
    }

    /**
     * Stage 3 — mid-stay enhancement request.
     *
     * <p>400 with {@code INSURANCE_BILL_NOT_LINKED} when no credit bill is bound
     * to the claim yet.
     */
    @PostMapping("/{insuranceId}/stages/enhancement")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> submitEnhancement(
            @PathVariable("insuranceId") UUID insuranceId,
            @Valid @RequestBody EnhancementStageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Enhancement request saved",
            deskService.submitEnhancement(insuranceId, req)));
    }

    /** Stage 4 — the TPA's decision on the enhancement. */
    @PostMapping("/{insuranceId}/stages/enhancement-approval")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> submitEnhancementApproval(
            @PathVariable("insuranceId") UUID insuranceId,
            @Valid @RequestBody EnhancementApprovalStageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Enhancement decision saved",
            deskService.submitEnhancementApproval(insuranceId, req)));
    }

    /** Stage 5 — pre-dispatch document checklist. */
    @PostMapping("/{insuranceId}/stages/checklist")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> submitChecklist(
            @PathVariable("insuranceId") UUID insuranceId,
            @Valid @RequestBody ChecklistStageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Check-list saved",
            deskService.submitChecklist(insuranceId, req)));
    }

    /** Stage 6 — courier or email dispatch of the claim docket. */
    @PostMapping("/{insuranceId}/stages/dispatch")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> submitDispatch(
            @PathVariable("insuranceId") UUID insuranceId,
            @Valid @RequestBody DispatchStageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Dispatch recorded",
            deskService.submitDispatch(insuranceId, req)));
    }

    /** Stage 7 — cheque receipts and itemised disallowances. */
    @PostMapping("/{insuranceId}/stages/disallowance")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> submitDisallowance(
            @PathVariable("insuranceId") UUID insuranceId,
            @Valid @RequestBody DisallowanceStageRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Settlement recorded",
            deskService.submitDisallowance(insuranceId, req)));
    }

    /** The whole desk view of one claim: every stage, its timestamps, and the cheques. */
    @GetMapping("/{insuranceId}/desk")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> getDeskView(
            @PathVariable("insuranceId") UUID insuranceId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", deskService.getDeskView(insuranceId)));
    }

    /**
     * GET /insurance?searchFromDate=&searchToDate=&stage= — the desk's landing query.
     *
     * <p>Previously a stub that accepted both dates and returned the pending
     * list regardless. Defaults to the last 30 days when no range is given.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<InsuranceDeskResponse>>> getByDateRange(
            @RequestParam(name = "searchFromDate", required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @RequestParam(name = "searchToDate", required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to,
            @RequestParam(name = "stage", required = false) InsuranceWorkflowStage stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok("OK", deskService.searchByDateRange(from, to, stage, pageable)));
    }

    /**
     * PUT /insurance/updateBillId — binds a patient credit bill to the claim.
     *
     * <p>Previously a stub: it parsed both ids and returned the record unchanged
     * with a success message, so the link never happened. Message text retains
     * the legacy spelling ("Liked") because the existing client matches on it.
     */
    @PutMapping("/updateBillId")
    public ResponseEntity<ApiResponse<InsuranceDeskResponse>> updateBillId(
            @RequestBody Map<String, Object> body) {
        Object rawId   = body.get("id");
        Object rawBill = body.get("billId");
        if (rawId == null || rawBill == null) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "INSURANCE_BILL_REQUIRED: both id and billId are required");
        }
        UUID insuranceId = UUID.fromString(rawId.toString());
        UUID billId      = UUID.fromString(rawBill.toString());
        return ResponseEntity.ok(ApiResponse.ok("Bill Liked successfully",
            deskService.linkBill(insuranceId, billId)));
    }

    // ── Reference data for the desk dropdowns ───────────────────────────────

    /** GET /insurance/preAuthType */
    @GetMapping("/preAuthType")
    public ResponseEntity<ApiResponse<InsurancePreAuthType[]>> getPreAuthTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", InsurancePreAuthType.values()));
    }

    /**
     * GET /insurance/modeOfCommunication — FAX | MAIL.
     *
     * <p>Narrowed from the previous free-form list. The desk flow pairs the mode
     * with a mandatory endpoint field, and only these two have one.
     */
    @GetMapping("/modeOfCommunication")
    public ResponseEntity<ApiResponse<ModeOfCommunication[]>> getModes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", ModeOfCommunication.values()));
    }

    /** GET /insurance/modeOfDispatch — COURIER | EMAIL. */
    @GetMapping("/modeOfDispatch")
    public ResponseEntity<ApiResponse<ModeOfDispatch[]>> getDispatchModes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", ModeOfDispatch.values()));
    }

    /** GET /insurance/courierVendors */
    @GetMapping("/courierVendors")
    public ResponseEntity<ApiResponse<CourierVendor[]>> getCourierVendors() {
        return ResponseEntity.ok(ApiResponse.ok("OK", CourierVendor.values()));
    }

    /** GET /insurance/insuranceStatus — the flat lifecycle enum. */
    @GetMapping("/insuranceStatus")
    public ResponseEntity<ApiResponse<InsuranceStatus[]>> getStatuses() {
        return ResponseEntity.ok(ApiResponse.ok("OK", InsuranceStatus.values()));
    }

    /** GET /insurance/tpaDecision — APPROVED | REJECTED. */
    @GetMapping("/tpaDecision")
    public ResponseEntity<ApiResponse<TpaDecision[]>> getTpaDecisions() {
        return ResponseEntity.ok(ApiResponse.ok("OK", TpaDecision.values()));
    }

    /**
     * GET /insurance/getStatus — the nine workflow stages as {id, name, label}.
     *
     * <p>{@code id} is the enum name, not an ordinal. The legacy system keyed
     * these by ordinal, which is why its spec carries a "DO NOT reorder"
     * warning; a client that stored a 6 would silently mean a different stage
     * the first time a value was inserted.
     */
    @GetMapping("/getStatus")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getStatusList() {
        var stages = java.util.Arrays.stream(InsuranceWorkflowStage.values())
            .map(s -> Map.of("id", s.name(), "name", s.name(), "label", s.label()))
            .toList();
        return ResponseEntity.ok(ApiResponse.ok("OK", stages));
    }

    /**
     * GET /insurance/getAgeingCriteria — the six receivables brackets.
     *
     * <p>Corrected to the six brackets the ageing report actually buckets by
     * (WO-021 D-3); the previous five did not match any report.
     */
    @GetMapping("/getAgeingCriteria")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getAgeingCriteria() {
        var criteria = List.of(
            Map.of("id", "1", "name", "Less than 31 days"),
            Map.of("id", "2", "name", "31 to 60 days"),
            Map.of("id", "3", "name", "61 to 90 days"),
            Map.of("id", "4", "name", "91 to 120 days"),
            Map.of("id", "5", "name", "121 to 150 days"),
            Map.of("id", "6", "name", "More than 150 days")
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", criteria));
    }
}
