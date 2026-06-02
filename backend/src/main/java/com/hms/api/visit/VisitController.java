package com.hms.api.visit;

import com.hms.api.shared.ApiResponse;
import com.hms.application.visit.VisitService;
import com.hms.domain.visit.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * VisitController — manages OP and IP clinical visit sessions.
 *
 * IMPORTANT: POST /visit ALWAYS creates OP visits regardless of client input.
 * IP visits are created ONLY by BedAllocationService.allocateBed().
 *
 * Called by: PatientService (check-in), DiagnosticService (OP routing),
 *            BillService (bill/visit link), BedAllocationService (active visit lookup).
 */
@RestController
@RequestMapping("/visit")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    /**
     * POST /visit — Creates OP visit. visitType FORCED to OP.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Visit>> create(@RequestBody Visit req) {
        Visit created = visitService.createOpVisit(
            req.getPatientId(), req.getConsultantId(),
            req.getAppointmentId(), req.getVisitMode());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Visit created successfully", created));
    }

    /**
     * GET /visit/patientVisitEntry/{patientId}?date=&consultantId=
     * Returns visit count for duplicate walk-in detection.
     */
    @GetMapping("/patientVisitEntry/{patientId}")
    public ResponseEntity<ApiResponse<Long>> countVisits(
            @PathVariable("patientId") UUID patientId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "consultantId", required = false) UUID consultantId) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        long count = consultantId != null
            ? visitService.countForDate(patientId, consultantId, queryDate)
            : visitService.getByPatient(patientId).stream()
                .filter(v -> v.getVisitDate().equals(queryDate)).count();
        return ResponseEntity.ok(ApiResponse.ok("OK", count));
    }

    /**
     * GET /visit?datesearch= — Visits for a date (OP, logged-in user's departments).
     */
    @GetMapping
    @PreAuthorize("hasPermission('OUT_PATIENT','')")
    public ResponseEntity<ApiResponse<List<Visit>>> getByDate(
            @RequestParam(name = "datesearch", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datesearch) {
        LocalDate date = datesearch != null ? datesearch : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("OK", visitService.getByDate(date)));
    }

    /**
     * GET /visit/patient/{patientId} — Full visit history for a patient.
     */
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<Visit>>> getByPatient(@PathVariable("patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", visitService.getByPatient(patientId)));
    }

    /**
     * PUT /visit — Updates a visit. Clears appointment FK if appointmentId=null.
     */
    @PutMapping
    @PreAuthorize("hasPermission('OUT_PATIENT','')")
    public ResponseEntity<ApiResponse<Visit>> update(@RequestBody Visit req) {
        if (req.getId() == null) {
            return (ResponseEntity) ResponseEntity.badRequest().body(ApiResponse.error("Visit id is required"));
        }
        return ResponseEntity.ok(ApiResponse.ok("Record saved successfully", visitService.updateVisit(req)));
    }

    /**
     * GET /visit/active/patient/{patientId}
     * Returns the currently active IP visit (bedStatus=true or billStatus=true).
     * Called by BedAllocationService before every bed allocation.
     */
    @GetMapping("/active/patient/{patientId}")
    public ResponseEntity<ApiResponse<Visit>> getActiveIpVisit(@PathVariable("patientId") UUID patientId) {
        return visitService.getActiveIpVisit(patientId)
            .map(v -> ResponseEntity.ok(ApiResponse.ok("OK", v)))
            .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No active IP visit", null)));
    }

    /**
     * GET /visit/{id} — Single visit by UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Visit>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", visitService.getById(id)));
    }

    /**
     * GET /visit/billId?billId= — Visit linked to a bill UUID.
     * Called by BillService.generateBill() to clear billStatus after IP bill finalisation.
     */
    @GetMapping("/billId")
    public ResponseEntity<ApiResponse<Visit>> getByBillId(@RequestParam(name = "billId") UUID billId) {
        return visitService.getByBillId(billId)
            .map(v -> ResponseEntity.ok(ApiResponse.ok("OK", v)))
            .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No visit found for bill", null)));
    }

    /**
     * GET /visit/getPatient?visitType=&date=&start=&limit=
     * Paginated patient list by visit type and date.
     * NOTE: returns null (not empty list) when no visits — matches legacy behaviour.
     */
    @GetMapping("/getPatient")
    public ResponseEntity<ApiResponse<List<Visit>>> getPatientsByVisitType(
            @RequestParam(name = "visitType", defaultValue = "OP") VisitType visitType,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        List<Visit> visits = visitService.getByTypeAndDate(visitType, queryDate, start, limit);
        if (visits.isEmpty()) return ResponseEntity.ok(ApiResponse.ok("No visits found", null));
        return ResponseEntity.ok(ApiResponse.ok("OK", visits));
    }

    /**
     * GET /visit/getVisitByDischargeDate?patientId=&dischargeDate=
     */
    @GetMapping("/getVisitByDischargeDate")
    public ResponseEntity<ApiResponse<Visit>> getByDischargeDate(
            @RequestParam(name = "patientId") UUID patientId,
            @RequestParam(name = "dischargeDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dischargeDate) {
        return visitService.getByPatientAndDischargeDate(patientId, dischargeDate)
            .map(v -> ResponseEntity.ok(ApiResponse.ok("OK", v)))
            .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No visit found", null)));
    }

    /**
     * GET /visit/getvisitStatus — VisitType enum as list.
     */
    @GetMapping("/getvisitStatus")
    public ResponseEntity<ApiResponse<Map<Integer, String>>> getVisitStatusTypes() {
        Map<Integer, String> map = new java.util.LinkedHashMap<>();
        for (VisitStatus s : VisitStatus.values()) map.put(s.ordinal(), s.name());
        return ResponseEntity.ok(ApiResponse.ok("OK", map));
    }

    /**
     * GET /visit/getVisitStatusTypes — VISITSTATUS enum map (ordinal → name).
     */
    @GetMapping("/getVisitStatusTypes")
    @PreAuthorize("hasPermission('OUT_PATIENT','')")
    public ResponseEntity<ApiResponse<Map<Integer, String>>> getStatusTypes() {
        return getVisitStatusTypes();
    }

    /**
     * POST /visit/checkinDischargeSummary — Creates IP visit + discharge summary together.
     */
    @PostMapping("/checkinDischargeSummary")
    @PreAuthorize("hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<Visit>> createWithDischargeSummary(@RequestBody Visit req) {
        Visit created = visitService.createIpVisit(
            req.getPatientId(), req.getConsultantId(),
            req.getVisitDate(), null);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Visit and Discharge Summary created successfully", created));
    }

    /**
     * GET /visit/getDischargeSummaryDetails?visitType=&searchDate=
     */
    @GetMapping("/getDischargeSummaryDetails")
    @PreAuthorize("hasPermission('IN_PATIENT','')")
    public ResponseEntity<ApiResponse<List<Visit>>> getDischargeSummaries(
            @RequestParam(name = "visitType", defaultValue = "IP") VisitType visitType,
            @RequestParam(name = "searchDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate searchDate) {
        LocalDate date = searchDate != null ? searchDate : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("OK", visitService.getByTypeAndDate(visitType, date, 0, 100)));
    }

    /**
     * POST /visit/saveOrUpdateShare — Updates the multi-consultant share JSON on a visit.
     * Accepts { visitId, consultantId, shareData } and merges into visit.share.
     */
    @PostMapping("/saveOrUpdateShare")
    public ResponseEntity<ApiResponse<Visit>> saveShare(
            @RequestBody Map<String, Object> body) {
        UUID visitId = UUID.fromString(body.get("visitId").toString());
        // Update visit status to CONSULTATION_STARTED when share is set
        Visit visit = visitService.getById(visitId);
        if (visit.getVisitStatus() == VisitStatus.CHECKEDIN
            || visit.getVisitStatus() == VisitStatus.CASESHEET_RECORDED) {
            visit.setVisitStatus(VisitStatus.CONSULTATION_STARTED);
            visitService.updateVisit(visit);
        }
        return ResponseEntity.ok(ApiResponse.ok("Share successfully saved", visitService.getById(visitId)));
    }
}
