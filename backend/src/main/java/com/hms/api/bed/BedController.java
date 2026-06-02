package com.hms.api.bed;

import com.hms.api.bed.request.AllocateBedRequest;
import com.hms.api.bed.request.CreateBedRequest;
import com.hms.api.bed.response.BedOccupancyResponse;
import com.hms.api.bed.response.BedResponse;
import com.hms.api.bed.response.BedStatusSummary;
import com.hms.api.shared.ApiResponse;
import com.hms.application.bed.BedManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/beds")
@RequiredArgsConstructor
public class BedController {

    private final BedManagementService bedService;

    @PostMapping
    public ResponseEntity<ApiResponse<BedResponse>> createBed(
            @Valid @RequestBody CreateBedRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Bed created", bedService.createBed(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BedResponse>> updateBed(
            @PathVariable UUID id,
            @Valid @RequestBody CreateBedRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Bed updated", bedService.updateBed(id, req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BedResponse>>> getAllBeds() {
        return ResponseEntity.ok(ApiResponse.ok("OK", bedService.getAllBeds()));
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<BedResponse>>> getAvailable(
            @RequestParam(name = "roomCategoryId", required = false) UUID roomCategoryId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            bedService.getAvailableBeds(roomCategoryId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<BedStatusSummary>> getSummary() {
        return ResponseEntity.ok(ApiResponse.ok("OK", bedService.getStatusSummary()));
    }

    @PostMapping("/allocate")
    public ResponseEntity<ApiResponse<BedOccupancyResponse>> allocate(
            @Valid @RequestBody AllocateBedRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Bed allocated",
            bedService.allocateBed(req)));
    }

    @PostMapping("/release/{bedId}")
    public ResponseEntity<ApiResponse<Void>> release(@PathVariable(name = "bedId") UUID bedId) {
        bedService.releaseBedByBedId(bedId);
        return ResponseEntity.ok(ApiResponse.ok("Bed released"));
    }

    @PostMapping("/{bedId}/maintenance")
    public ResponseEntity<ApiResponse<BedResponse>> setMaintenance(@PathVariable(name = "bedId") UUID bedId) {
        return ResponseEntity.ok(ApiResponse.ok("Bed set to maintenance",
            bedService.setMaintenance(bedId)));
    }

    @DeleteMapping("/{bedId}/maintenance")
    public ResponseEntity<ApiResponse<BedResponse>> clearMaintenance(@PathVariable(name = "bedId") UUID bedId) {
        return ResponseEntity.ok(ApiResponse.ok("Bed cleared from maintenance",
            bedService.clearMaintenance(bedId)));
    }

    @GetMapping("/occupancy/{encounterId}")
    public ResponseEntity<ApiResponse<List<BedOccupancyResponse>>> getOccupancyHistory(
            @PathVariable(name = "encounterId") UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            bedService.getOccupancyHistory(encounterId)));
    }

    // ─── SRS-named bed management endpoints ──────────────────────────────────

    /** GET /bed/getAvailable — beds where bedStatus=AVAILABLE (mirrors legacy URL) */
    @GetMapping("/getAvailable")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('BEDMANAGEMENT','')")
    public ResponseEntity<ApiResponse<List<BedResponse>>> getAvailableLegacy(
            @RequestParam(name = "roomCategoryId", required = false) java.util.UUID roomCategoryId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            bedService.getAvailableBeds(roomCategoryId)));
    }

    /**
     * GET /bed/getAllocatedDetail — all non-AVAILABLE beds with visit/patient/bedType data.
     * Groups beds with their current occupancy, encounter and patient context.
     * Used by bed management dashboard grid.
     */
    @GetMapping("/getAllocatedDetail")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('BEDMANAGEMENT','')")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getAllocatedDetail() {
        return ResponseEntity.ok(ApiResponse.ok("OK", bedService.getAllocatedDetail()));
    }

    /** GET /bed/years — year range from first bed allocation to now */
    @GetMapping("/years")
    public ResponseEntity<ApiResponse<List<Integer>>> getAllocationYears() {
        return ResponseEntity.ok(ApiResponse.ok("OK", bedService.getAllocationYears()));
    }

    /**
     * POST /bed/allocateBed — allocates a bed (SRS-named, mirrors /allocate).
     * Uses existing AllocateBedRequest; delegate to same service method.
     */
    @PostMapping("/allocateBed")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('BEDMANAGEMENT','')")
    public ResponseEntity<ApiResponse<com.hms.api.bed.response.BedOccupancyResponse>> allocateBed(
            @RequestBody com.hms.api.bed.request.AllocateBedRequest req) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
            .body(ApiResponse.ok("Bed allocated successfully", bedService.allocateBed(req)));
    }

    /**
     * POST /bed/transferBed — transfers patient to a new bed.
     * Body: { encounterId, newBedId, fromDate (ISO date) }
     */
    @PostMapping("/transferBed")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('BEDMANAGEMENT','')")
    public ResponseEntity<ApiResponse<com.hms.api.bed.response.BedOccupancyResponse>> transferBed(
            @RequestBody java.util.Map<String, String> body) {
        java.util.UUID encounterId = java.util.UUID.fromString(body.get("encounterId"));
        java.util.UUID newBedId    = java.util.UUID.fromString(body.get("newBedId"));
        java.time.LocalDate fromDate = body.containsKey("fromDate")
            ? java.time.LocalDate.parse(body.get("fromDate"))
            : java.time.LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("Bed transferred successfully",
            bedService.transferBed(encounterId, newBedId, fromDate)));
    }

    /**
     * POST /bed/vacateBed — discharges a patient and releases their bed.
     * Body: { encounterId, dischargeDate (ISO date, optional) }
     */
    @PostMapping("/vacateBed")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('BEDMANAGEMENT','')")
    public ResponseEntity<ApiResponse<Void>> vacateBed(
            @RequestBody java.util.Map<String, String> body) {
        java.util.UUID encounterId = java.util.UUID.fromString(body.get("encounterId"));
        java.time.LocalDate dischargeDate = body.containsKey("dischargeDate")
            ? java.time.LocalDate.parse(body.get("dischargeDate"))
            : java.time.LocalDate.now();
        bedService.vacateBed(encounterId, dischargeDate);
        return ResponseEntity.ok(ApiResponse.ok("Bed vacated successfully"));
    }

    /**
     * GET /beds/search-inpatients?q=SCMCP-0001  (or patient name fragment)
     * Returns up to 10 active inpatient encounters matching the query,
     * with encounterId, patientNumber and patientName so the UI can
     * show a meaningful autocomplete instead of a raw UUID input.
     */
    @GetMapping("/search-inpatients")
    public ResponseEntity<ApiResponse<java.util.List<com.hms.api.bed.response.InpatientSearchResult>>> searchInpatients(
            @RequestParam(name = "q", defaultValue = "") String q) {
        return ResponseEntity.ok(ApiResponse.ok("OK", bedService.searchInpatients(q)));
    }

    

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<BedResponse>>> getPaginated(
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String value) {
        
        List<BedResponse> all = bedService.getAllBeds();
        
        if (value != null && !value.isBlank()) {
            String lowerValue = value.toLowerCase();
            all = all.stream().filter(e -> {
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("name");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getName");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getFirstName");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getUsername");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                try {
                    java.lang.reflect.Method m = e.getClass().getMethod("getPrefix");
                    Object res = m.invoke(e);
                    if (res != null && res.toString().toLowerCase().contains(lowerValue)) return true;
                } catch(Exception ex) {}
                return false;
            }).toList();
        }
        
        int total = all.size();
        int startIndex = Math.min(start, total);
        int endIndex = Math.min(start + limit, total);
        List<BedResponse> pageContent = startIndex <= endIndex ? all.subList(startIndex, endIndex) : new java.util.ArrayList<>();
        
        org.springframework.data.domain.Page<BedResponse> page = new org.springframework.data.domain.PageImpl<>(
            pageContent, 
            org.springframework.data.domain.PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1)), 
            total
        );
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

}
