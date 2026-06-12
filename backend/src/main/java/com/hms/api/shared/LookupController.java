package com.hms.api.shared;

import com.hms.api.encounter.response.EncounterSummaryResponse;
import com.hms.application.encounter.EncounterManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/lookup-service")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('IN_PATIENT','') or hasPermission('OUT_PATIENT','') or hasPermission('PATIENT_BILLS','') or hasPermission('SALES','') or hasPermission('LAB_REPORT','') or hasPermission('RADIOLOGY','')")
public class LookupController {

    private final EncounterManagementService encounterService;

    @GetMapping("/inpatients")
    public ResponseEntity<ApiResponse<List<EncounterSummaryResponse>>> getActiveInpatients() {
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterService.findActiveInpatientsWithBeds()));
    }
}
