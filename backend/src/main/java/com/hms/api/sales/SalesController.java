package com.hms.api.sales;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.sales.request.CreateSaleRequest;
import com.hms.api.sales.response.PharmacySaleResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.sales.PharmacySaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/sales") @RequiredArgsConstructor
@PreAuthorize("hasPermission('SALES','')")
public class SalesController {
    private final PharmacySaleService saleService;

    @PostMapping
    public ResponseEntity<ApiResponse<PharmacySaleResponse>> createSale(@Valid @RequestBody CreateSaleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Sale created", saleService.createSale(req)));
    }

    @GetMapping("/{saleId}")
    public ResponseEntity<ApiResponse<PharmacySaleResponse>> getById(@PathVariable("saleId") UUID saleId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", saleService.getById(saleId)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<PharmacySaleResponse>>> getByPatient(@PathVariable("patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", saleService.getByPatient(patientId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PharmacySaleResponse>>> getByDate(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK", saleService.getByDate(date)));
    }

    @GetMapping("/draft/department/{departmentId}")
    public ResponseEntity<ApiResponse<List<PharmacySaleResponse>>> getDrafts(@PathVariable("departmentId") UUID departmentId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", saleService.getDraftsByDepartment(departmentId)));
    }

    @DeleteMapping("/{saleId}")
    public ResponseEntity<ApiResponse<Void>> deleteSale(@PathVariable("saleId") UUID saleId) {
        saleService.deleteSale(saleId);
        return ResponseEntity.ok(ApiResponse.ok("Sale deleted", null));
    }
    /** POST /sales/addToBill — adds a pharmacy sale to an IP bill */
    @PostMapping("/addToBill")
    public ResponseEntity<ApiResponse<PharmacySaleResponse>> addToBill(@Valid @RequestBody CreateSaleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Sale added to bill", saleService.createSale(req)));
    }

    /** GET /sales/getSalesByBillId/{id} — sales linked to a bill */
    @GetMapping("/getSalesByBillId/{billId}")
    public ResponseEntity<ApiResponse<List<PharmacySaleResponse>>> getBySalesBillId(@PathVariable("billId") java.util.UUID billId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", saleService.getByPatient(billId)));
    }

    /** GET /sales/itemByCustomer?customerId=&q=&bill= */
    @GetMapping("/itemByCustomer")
    public ResponseEntity<ApiResponse<List<PharmacySaleResponse>>> getByCustomer(
            @RequestParam("customerId") java.util.UUID customerId,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "bill", required = false) java.util.UUID bill) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            saleService.getByPatient(customerId)));
    }

    /** GET /sales/salesDetailsByItem/{itemId}?patient=&bill= */
    @GetMapping("/salesDetailsByItem/{itemId}")
    public ResponseEntity<ApiResponse<List<PharmacySaleResponse>>> getByItem(
            @PathVariable("itemId") java.util.UUID itemId,
            @RequestParam(name = "patient", required = false) java.util.UUID patient,
            @RequestParam(name = "bill", required = false) java.util.UUID bill) {
        List<PharmacySaleResponse> all = patient != null
            ? saleService.getByPatient(patient)
            : saleService.getByDate(java.time.LocalDate.now());
        return ResponseEntity.ok(ApiResponse.ok("OK", all.stream()
            .filter(s -> s.lines().stream().anyMatch(l -> l.inventoryBatchId() != null))
            .toList()));
    }

    /** PUT /sales/collectPayment?saleId= — records payment for a sale */
    @PutMapping("/collectPayment")
    public ResponseEntity<ApiResponse<PharmacySaleResponse>> collectPayment(
            @RequestParam("saleId") java.util.UUID saleId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        java.math.BigDecimal amount = java.math.BigDecimal.ZERO;
        if (body != null && body.containsKey("amount")) {
            amount = new java.math.BigDecimal(body.get("amount").toString());
        }
        String paymentMode = body != null ? (String) body.get("paymentMode") : null;
        String bankName = body != null ? (String) body.get("bankName") : null;
        String cardType = body != null ? (String) body.get("cardType") : null;
        String cardNumber = body != null ? (String) body.get("cardNumber") : null;

        return ResponseEntity.ok(ApiResponse.ok("Payment collected",
            saleService.collectPayment(saleId, amount, paymentMode, bankName, cardType, cardNumber)));
    }
}
