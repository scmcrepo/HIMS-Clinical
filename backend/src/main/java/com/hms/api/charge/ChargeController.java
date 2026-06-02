package com.hms.api.charge;
import com.hms.api.shared.ApiResponse;
import com.hms.application.charge.ChargeService;
import com.hms.domain.charge.model.Charge;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/charge") @RequiredArgsConstructor
public class ChargeController {
    private final ChargeService chargeService;

    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_CHARGES','')")
    public ResponseEntity<ApiResponse<Charge>> create(@RequestBody Charge req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Charge information saved successfully", chargeService.createCharge(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Charge>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chargeService.getById(id)));
    }

    @PutMapping
    @PreAuthorize("hasPermission('SETTINGS_CHARGES','')")
    public ResponseEntity<ApiResponse<Charge>> update(@RequestBody Charge req) {
        if (req.getId() == null) return (ResponseEntity) ResponseEntity.badRequest().body(ApiResponse.error("id is required"));
        return ResponseEntity.ok(ApiResponse.ok("Charge information updated successfully",
            chargeService.updateCharge(req.getId(), req)));
    }

    @GetMapping("/getChargeByName")
    public ResponseEntity<ApiResponse<List<Charge>>> searchByName(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "payor", required = false) UUID payor,
            @RequestParam(name = "categoryType", required = false) String categoryType) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chargeService.search(name)));
    }

    @GetMapping("/search/name/{searchTerm}")
    public ResponseEntity<ApiResponse<List<Charge>>> searchByTerm(@PathVariable("searchTerm") String searchTerm) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chargeService.search(searchTerm)));
    }

    @GetMapping("/getChargeByCategory")
    public ResponseEntity<ApiResponse<List<Charge>>> getByCategory(@RequestParam(name = "categoryType") UUID categoryType) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chargeService.getByCategory(categoryType)));
    }

    @GetMapping
    @PreAuthorize("hasPermission('SETTINGS_CHARGES','')")
    public ResponseEntity<ApiResponse<List<Charge>>> getAll(@RequestParam(name = "value", required = false) String value) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chargeService.search(value)));
    }

    @PostMapping("/diagnosticOrderCheckBoxChargeIds")
    @PreAuthorize("hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<List<Charge>>> getByIds(@RequestBody List<UUID> ids) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chargeService.getByIds(ids)));
    }

    @GetMapping("/validateDelete/{id}")
    public ResponseEntity<ApiResponse<String>> validateDelete(@PathVariable("id") UUID id) {
        String msg = chargeService.validateDelete(id);
        return ResponseEntity.ok(ApiResponse.ok("OK", msg));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") UUID id) {
        chargeService.deleteCharge(id);
        return ResponseEntity.ok(ApiResponse.ok("Charge information deleted successfully"));
    }

    /**
     * GET /charge/getSurgeryChargeByName?name= — surgery charge lookup for OT.
     * Requires OT_SCHEDULE permission.
     */
    @GetMapping("/getSurgeryChargeByName")
    @PreAuthorize("hasPermission('OT_SCHEDULE','')")
    public ResponseEntity<ApiResponse<List<Charge>>> getSurgeryByName(
            @RequestParam(name = "name", defaultValue = "") String name) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chargeService.search(name)));
    }

    @GetMapping("/page")
    @PreAuthorize("hasPermission('SETTINGS_CHARGES','')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Charge>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {

        List<Charge> all = chargeService.searchAll(value);

        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<Charge> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();

        org.springframework.data.domain.Page<Charge> page = new org.springframework.data.domain.PageImpl<>(
                pageContent,
                org.springframework.data.domain.PageRequest.of(start / limit, limit),
                total
        );

        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

    /**
     * GET /charge/getChargeByCategoryType?categoryType=&chargeType=&start=&limit=&value=
     * Paginated charge search with category type and charge type filters.
     */
    @GetMapping("/getChargeByCategoryType")
    public ResponseEntity<ApiResponse<List<Charge>>> getChargeByCategoryType(
            @RequestParam(required = false) java.util.UUID categoryType,
            @RequestParam(name = "chargeType", required = false) String chargeType,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "value", required = false) String value) {
        List<Charge> charges = categoryType != null
            ? chargeService.getByCategory(categoryType)
            : chargeService.search(value);
        int from = Math.min(start, charges.size());
        int to   = Math.min(from + limit, charges.size());
        return ResponseEntity.ok(ApiResponse.ok("OK", charges.subList(from, to)));
    }
}
