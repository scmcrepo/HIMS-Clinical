package com.hms.api.encounter;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.encounter.request.*;
import com.hms.api.encounter.response.EncounterResponse;
import com.hms.api.encounter.response.EncounterSummaryResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.encounter.EncounterManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/encounters") @RequiredArgsConstructor
public class EncounterController {

    private final EncounterManagementService encounterService;

    @GetMapping
    @PreAuthorize("hasPermission('OUT_PATIENT','') or hasPermission('IN_PATIENT','') or hasPermission('SALES','') or hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<Page<EncounterSummaryResponse>>> getAll(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterService.findAll(query, date, pageable)));
    }

    @GetMapping("/active-inpatients")
    @PreAuthorize("hasPermission('IN_PATIENT','') or hasPermission('OUT_PATIENT','') or hasPermission('PATIENT_BILLS','')")
    public ResponseEntity<ApiResponse<Page<EncounterSummaryResponse>>> getActiveInpatients(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "consultantId", required = false) UUID consultantId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterService.findActiveInpatients(query, date, consultantId, pageable)));
    }

    @GetMapping("/admission-requests")
    @PreAuthorize("hasPermission('IN_PATIENT','') or hasPermission('OUT_PATIENT','')")
    public ResponseEntity<ApiResponse<Page<EncounterSummaryResponse>>> getPendingAdmissionRequests(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "consultantId", required = false) UUID consultantId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "5") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterService.findPendingAdmissionRequestsPaged(query, consultantId, pageable)));
    }

    @GetMapping("/today-outpatients")
    @PreAuthorize("hasPermission('OUT_PATIENT','') or hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<Page<EncounterSummaryResponse>>> getTodayOutpatients(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "consultantId", required = false) UUID consultantId,
            @RequestParam(name = "status", required = false) com.hms.domain.encounter.model.EncounterStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterService.findTodayOutpatients(query, date, consultantId, status, pageable)));
    }

    @PostMapping("/outpatient")
    @PreAuthorize("hasPermission('OUT_PATIENT','')")
    public ResponseEntity<ApiResponse<EncounterResponse>> createOutpatient(@Valid @RequestBody CreateEncounterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Encounter created", encounterService.createOutpatientEncounter(req)));
    }

    @PostMapping("/inpatient")
    @PreAuthorize("hasPermission('IN_PATIENT','') or hasPermission('OUT_PATIENT','')")
    public ResponseEntity<ApiResponse<EncounterResponse>> createInpatient(@Valid @RequestBody CreateEncounterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Encounter created", encounterService.createInpatientEncounter(req)));
    }

    @GetMapping("/{encounterId}")
    public ResponseEntity<ApiResponse<EncounterResponse>> getById(@PathVariable("encounterId") UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterService.findById(encounterId)));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<Page<EncounterSummaryResponse>>> getByPatient(
            @PathVariable("patientId") UUID patientId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterService.findByPatient(patientId, pageable)));
    }

    @GetMapping("/patient/{patientId}/active-inpatient")
    public ResponseEntity<ApiResponse<EncounterResponse>> getActiveInpatient(@PathVariable("patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterService.getActiveInpatient(patientId)));
    }

    @PutMapping("/{encounterId}")
    @PreAuthorize("hasPermission('OUT_PATIENT','') or hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<EncounterResponse>> update(
            @PathVariable("encounterId") UUID encounterId, @RequestBody UpdateEncounterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Updated", encounterService.updateEncounter(encounterId, req)));
    }

    @PostMapping("/{encounterId}/vitals")
    @PreAuthorize("hasPermission('OUT_PATIENT','') or hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<EncounterResponse>> recordVitals(
            @PathVariable("encounterId") UUID encounterId, @Valid @RequestBody RecordVitalsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Vitals recorded", encounterService.recordVitals(encounterId, req)));
    }

    @PostMapping("/{encounterId}/casesheet")
    @PreAuthorize("hasPermission('OUT_PATIENT','') or hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<EncounterResponse>> recordCasesheet(
            @PathVariable("encounterId") UUID encounterId, @RequestBody RecordCasesheetRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Casesheet recorded", encounterService.recordCasesheet(encounterId, req)));
    }

    @PostMapping("/{encounterId}/discharge")
    @PreAuthorize("hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<EncounterResponse>> discharge(
            @PathVariable("encounterId") UUID encounterId, @RequestBody DischargeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Patient discharged", encounterService.discharge(encounterId, req)));
    }

    @PutMapping("/{encounterId}/consultant-share/{consultantId}")
    @PreAuthorize("hasPermission('OUT_PATIENT','') or hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<Void>> updateConsultantShare(
            @PathVariable("encounterId") UUID encounterId, @PathVariable("consultantId") String consultantId,
            @RequestBody Map<String, Object> shareData) {
        encounterService.updateConsultantShare(encounterId, consultantId, shareData);
        return ResponseEntity.ok(ApiResponse.ok("Consultant share updated"));
    }

    @DeleteMapping("/{encounterId}")
    @PreAuthorize("hasPermission('OUT_PATIENT','') or hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<EncounterResponse>> cancel(@PathVariable("encounterId") UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("Encounter cancelled", encounterService.cancelEncounter(encounterId)));
    }
}
