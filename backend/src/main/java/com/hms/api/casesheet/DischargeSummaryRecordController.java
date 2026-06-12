package com.hms.api.casesheet;

import com.hms.api.casesheet.request.SaveRecordRequest;
import com.hms.api.casesheet.response.DischargeRecordResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.casesheet.DischargeSummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/encounters/{encounterId}/discharge-summary-records")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('MEDICAL_RECORD','')")
public class DischargeSummaryRecordController {

    private final DischargeSummaryService svc;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DischargeRecordResponse>>> list(
            @PathVariable UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", svc.getRecordsByEncounter(encounterId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DischargeRecordResponse>> save(
            @PathVariable UUID encounterId,
            @Valid @RequestBody SaveRecordRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Discharge summary saved", svc.saveRecord(encounterId, req)));
    }
}
