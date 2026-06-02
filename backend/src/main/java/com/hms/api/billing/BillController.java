package com.hms.api.billing;

import com.hms.api.billing.request.*;
import com.hms.api.billing.response.*;
import com.hms.api.shared.ApiResponse;
import com.hms.application.billing.BillingOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/bills")
@RequiredArgsConstructor
public class BillController {
    private final BillingOperationsService billingService;

    @GetMapping({"", "/all"})
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<List<BillSummaryResponse>>> getAllBills() {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getAllBills()));
    }

    // ─── Create ──────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> createBill(
            @RequestParam(name = "isDraft", defaultValue = "true") boolean isDraft,
            @Valid @RequestBody CreateBillRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Bill generated successfully", billingService.createBill(req)));
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    @GetMapping("/{billId}")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> getBill(@PathVariable(name = "billId") UUID billId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getBillById(billId)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<List<BillSummaryResponse>>> getBillsByPatientRest(@PathVariable(name = "patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getBillsByPatient(patientId)));
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<List<BillSummaryResponse>>> getBillsByPatient(@RequestParam(name = "patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getBillsByPatient(patientId)));
    }

    @GetMapping("/by-visit")
    @PreAuthorize("hasPermission('IP_AUTOMATED_ORDERS','')")
    public ResponseEntity<ApiResponse<BillResponse>> getBillByVisit(@RequestParam(name = "visit") UUID visit) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getBillByVisit(visit)));
    }

    @GetMapping("/current-month/{patientId}")
    public ResponseEntity<ApiResponse<List<BillSummaryResponse>>> currentMonthBill(@PathVariable(name = "patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getCurrentMonthBills(patientId)));
    }

    @GetMapping("/history/search")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<BillSummaryResponse>>> searchBills(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "from", required = false) java.time.LocalDate from,
            @RequestParam(name = "to", required = false) java.time.LocalDate to,
            @org.springframework.data.web.PageableDefault(size = 5) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.searchBills(q, from, to, pageable)));
    }

    // ─── Payments ────────────────────────────────────────────────────────────

    @PutMapping("/collect-payment")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> recordPayment(
            @RequestParam(name = "billId") UUID billId, @Valid @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Payment recorded successfully", billingService.recordPayment(billId, req)));
    }

    @PostMapping("/{billId}/payments")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> recordPaymentRest(
            @PathVariable(name = "billId") UUID billId, @Valid @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Payment recorded", billingService.recordPayment(billId, req)));
    }

    @PutMapping("/refund")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> refundPayment(
            @RequestParam(name = "billId") UUID billId, @RequestBody RefundRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Refund processed", billingService.refund(billId, req)));
    }

    @PostMapping("/{billId}/refunds")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> refundRest(
            @PathVariable(name = "billId") UUID billId, @Valid @RequestBody RefundRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Refund processed", billingService.refund(billId, req)));
    }

    // ─── Generation ──────────────────────────────────────────────────────────

    @PutMapping("/generate")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> generateBill(
            @RequestParam(name = "billId") UUID billId, @Valid @RequestBody(required = false) GenerateBillRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Bill generated successfully",
            billingService.generateBill(billId, req != null ? req : new GenerateBillRequest(null, null))));
    }

    @PostMapping("/{billId}/generate")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> generateBillRest(
            @PathVariable(name = "billId") UUID billId, @Valid @RequestBody GenerateBillRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Bill generated", billingService.generateBill(billId, req)));
    }

    // ─── Discount ────────────────────────────────────────────────────────────

    @PutMapping("/discount")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> applyDiscount(
            @RequestParam(name = "billId") UUID billId, @Valid @RequestBody ApplyDiscountRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Discount applied", billingService.applyDiscount(billId, req)));
    }

    @PostMapping("/{billId}/discounts")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> applyDiscountRest(
            @PathVariable(name = "billId") UUID billId, @Valid @RequestBody ApplyDiscountRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Discount applied", billingService.applyDiscount(billId, req)));
    }

    @DeleteMapping("/discount")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> cancelDiscount(@RequestParam(name = "billId") UUID billId) {
        return ResponseEntity.ok(ApiResponse.ok("Discount cancelled", billingService.cancelDiscount(billId)));
    }

    @DeleteMapping("/{billId}/discounts")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> cancelDiscountRest(@PathVariable(name = "billId") UUID billId) {
        return ResponseEntity.ok(ApiResponse.ok("Discount cancelled", billingService.cancelDiscount(billId)));
    }

    // ─── Charge management ───────────────────────────────────────────────────

    @PutMapping("/add-charge")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> addCharge(
            @RequestParam(name = "billId") UUID billId, @Valid @RequestBody AddChargeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Charge added", billingService.addChargeLineItem(billId, req)));
    }

    @PostMapping("/{billId}/charges")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> addChargeRest(
            @PathVariable(name = "billId") UUID billId, @Valid @RequestBody AddChargeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Charge added", billingService.addChargeLineItem(billId, req)));
    }

    @PutMapping("/remove-charge")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> removeCharge(
            @RequestParam(name = "billId") UUID billId, @RequestParam(name = "lineItemId") UUID lineItemId,
            @RequestParam(name = "reason", defaultValue = "Removed by user") String reason) {
        return ResponseEntity.ok(ApiResponse.ok("Charge removed", billingService.removeChargeLineItem(billId, lineItemId, reason)));
    }

    @DeleteMapping("/{billId}/charges/{lineItemId}")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> removeChargeRest(
            @PathVariable(name = "billId") UUID billId, @PathVariable(name = "lineItemId") UUID lineItemId,
            @RequestParam(name = "reason", defaultValue = "Removed by user") String reason) {
        return ResponseEntity.ok(ApiResponse.ok("Charge removed", billingService.removeChargeLineItem(billId, lineItemId, reason)));
    }

    @PutMapping("/update-charge")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> updateCharge(
            @RequestParam(name = "billId") UUID billId, @RequestBody Map<String, Object> body) {
        UUID lineItemId = UUID.fromString(body.get("lineItemId").toString());
        long newRate    = Long.parseLong(body.get("rate").toString());
        int  newQty     = Integer.parseInt(body.get("quantity").toString());
        long discount   = Long.parseLong(body.getOrDefault("discount", "0").toString());
        String reason   = body.getOrDefault("reason", "Charge updated").toString();
        return ResponseEntity.ok(ApiResponse.ok("Charge updated",
            billingService.updateChargeLineItem(billId, lineItemId, newRate, newQty, discount, reason)));
    }

    @PutMapping("/update-details")
    public ResponseEntity<ApiResponse<Void>> updateBillDetails(@RequestBody List<Map<String, Object>> lines) {
        billingService.updateDisallowedAmounts(lines);
        return ResponseEntity.ok(ApiResponse.ok("Bill details updated"));
    }

    @PutMapping("/add-charge-by-visit")
    @PreAuthorize("hasPermission('IP_AUTOMATED_OTHER_CHARGE','')")
    public ResponseEntity<ApiResponse<BillResponse>> addChargeByVisit(
            @RequestParam(name = "visitId") UUID visitId, @Valid @RequestBody AddChargeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Charge added",
            billingService.addChargeByVisit(visitId, req)));
    }

    // ─── Audit history ───────────────────────────────────────────────────────

    @GetMapping("/edit-history/{billDetailId}")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEditHistory(@PathVariable(name = "billDetailId") UUID billDetailId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getEditHistory(billDetailId)));
    }

    @GetMapping("/removed-history/{billId}")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<List<Object>>> getRemovedHistory(@PathVariable(name = "billId") UUID billId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getRemovedChargeHistory(billId)));
    }

    @GetMapping("/package-details/{packageId}")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<List<Object>>> getPackageDetails(
            @PathVariable(name = "packageId") UUID packageId, @RequestParam(name = "billId") UUID billId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getPackageChargeLines(billId, packageId)));
    }

    /** GET /bill/getBillDetailOrder?visit= — ordered charge lines for a visit */
    @GetMapping("/visit-order")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<BillResponse>> getBillDetailOrder(@RequestParam(name = "visit") UUID visit) {
        return ResponseEntity.ok(ApiResponse.ok("OK", billingService.getBillByVisit(visit)));
    }
}
