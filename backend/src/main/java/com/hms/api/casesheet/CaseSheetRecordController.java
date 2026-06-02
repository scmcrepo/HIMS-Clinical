package com.hms.api.casesheet;

import com.hms.api.casesheet.request.SaveRecordRequest;
import com.hms.api.casesheet.response.CaseSheetRecordResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.casesheet.CaseSheetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Doctor-facing API for case sheet records, nested under encounters.
 *
 * GET    /encounters/{encounterId}/case-sheet-records        — list all records
 * GET    /encounters/{encounterId}/case-sheet-records/{id}   — single record
 * POST   /encounters/{encounterId}/case-sheet-records        — create or update (upsert)
 * DELETE /encounters/{encounterId}/case-sheet-records/{id}   — soft delete
 *
 * First POST advances encounter status → CASESHEET_RECORDED.
 */
@RestController
@RequestMapping("/encounters/{encounterId}/case-sheet-records")
@RequiredArgsConstructor
public class CaseSheetRecordController {

    private final CaseSheetService svc;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CaseSheetRecordResponse>>> list(
            @PathVariable UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", svc.getRecordsByEncounter(encounterId)));
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<ApiResponse<CaseSheetRecordResponse>> getById(
            @PathVariable UUID encounterId,
            @PathVariable UUID recordId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", svc.getRecord(recordId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CaseSheetRecordResponse>> save(
            @PathVariable UUID encounterId,
            @Valid @RequestBody SaveRecordRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Case sheet saved", svc.saveRecord(encounterId, req)));
    }

    @DeleteMapping("/{recordId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID encounterId,
            @PathVariable UUID recordId) {
        svc.deleteRecord(recordId);
        return ResponseEntity.ok(ApiResponse.ok("Record deleted"));
    }
}
