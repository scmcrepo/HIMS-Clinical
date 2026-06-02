package com.hms.api.opip;

import com.hms.api.casesheet.request.SaveRecordRequest;
import com.hms.api.casesheet.response.CaseSheetRecordResponse;
import com.hms.api.casesheet.response.CaseSheetTemplateDetail;
import com.hms.api.encounter.request.RecordVitalsRequest;
import com.hms.api.encounter.request.UpdateEncounterRequest;
import com.hms.api.encounter.response.EncounterResponse;
import com.hms.api.encounter.response.EncounterSummaryResponse;
import com.hms.api.opip.request.AdmissionReferralRequest;
import com.hms.application.diagnostic.DiagnosticOrderingService;
import com.hms.api.diagnostic.request.PlaceOrderRequest;
import com.hms.domain.diagnostic.model.DiagnosticType;
import com.hms.api.opip.request.AddPrescriptionRequest;
import com.hms.api.opip.request.AddDiagnosticOrderRequest;
import com.hms.api.opip.response.PrescriptionResponse;
import com.hms.api.opip.response.VisitDiagnosticOrderResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.casesheet.CaseSheetService;
import com.hms.application.encounter.EncounterManagementService;
import com.hms.domain.casesheet.model.CaseSheetVisitType;
import com.hms.domain.encounter.model.EncounterStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OP Queue Controller — convenience wrapper driving the outpatient state machine.
 *
 * State machine:
 *   CHECKED_IN → CONSULTATION_STARTED (after vitals)
 *               → CASESHEET_RECORDED  (after first casesheet save)
 *               → BILLING_DONE        (after mark-consulted)
 *
 * Endpoints:
 *   GET  /op-queue                             — today's OP queue
 *   GET  /op-queue/{id}/vitals                 — get recorded vitals
 *   POST /op-queue/{id}/vitals                 — nurse records vitals
 *   GET  /op-queue/{id}/casesheet              — load template + existing record
 *   POST /op-queue/{id}/casesheet              — doctor saves casesheet
 *   POST /op-queue/{id}/mark-consulted         — finalize encounter
 *   POST /op-queue/{id}/admit                  — request IP admission
 */
@RestController
@RequestMapping("/op-queue")
@RequiredArgsConstructor
@Slf4j
public class OutpatientQueueController {

    private final EncounterManagementService encounterSvc;
    private final CaseSheetService           casesheetSvc;
    private final DiagnosticOrderingService  diagnosticSvc;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EncounterSummaryResponse>>> getQueue(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable p = PageRequest.of(page, size, Sort.by("startedAt").ascending());
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterSvc.findTodayOutpatients(query, p)));
    }

    // ── Vitals ────────────────────────────────────────────────────────────────

    @GetMapping("/{encounterId}/vitals")
    public ResponseEntity<ApiResponse<EncounterResponse>> getVitals(@PathVariable UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterSvc.findById(encounterId)));
    }

    @PostMapping("/{encounterId}/vitals")
    public ResponseEntity<ApiResponse<EncounterResponse>> recordVitals(
            @PathVariable UUID encounterId,
            @Valid @RequestBody RecordVitalsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Vitals recorded", encounterSvc.recordVitals(encounterId, req)));
    }

    // ── CaseSheet ─────────────────────────────────────────────────────────────

    /**
     * Load the casesheet for an OP encounter.
     * Returns:
     *   - template: the form definition (null if not yet resolvable)
     *   - records:  any previously saved data
     * Query params: specialization (e.g. ORTHOPAEDICS), visitType (OP)
     */
    @GetMapping("/{encounterId}/casesheet")
    public ResponseEntity<ApiResponse<CasesheetLoadResponse>> loadCasesheet(
            @PathVariable UUID encounterId,
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) String visitType) {

        List<CaseSheetRecordResponse> records = casesheetSvc.getRecordsByEncounter(encounterId);

        CaseSheetTemplateDetail template = null;
        if (!records.isEmpty()) {
            template = casesheetSvc.getTemplate(records.get(0).template().id());
        } else {
            try {
                String spec = specialization;
                if (spec == null || spec.isBlank() || "ORTHOPAEDICS".equalsIgnoreCase(spec)) {
                    spec = casesheetSvc.getSpecializationForEncounter(encounterId);
                }
                CaseSheetVisitType vt = (visitType != null)
                        ? CaseSheetVisitType.valueOf(visitType.toUpperCase())
                        : CaseSheetVisitType.OP;
                template = casesheetSvc.getDefaultTemplate(spec, vt);
            } catch (Exception ignored) { /* no default — UI shows template picker */ }
        }
        return ResponseEntity.ok(ApiResponse.ok("OK", new CasesheetLoadResponse(template, records)));
    }

    @PostMapping("/{encounterId}/casesheet")
    public ResponseEntity<ApiResponse<CaseSheetRecordResponse>> saveCasesheet(
            @PathVariable UUID encounterId,
            @Valid @RequestBody SaveRecordRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Casesheet saved", casesheetSvc.saveRecord(encounterId, req)));
    }

    // ── Mark Consulted ────────────────────────────────────────────────────────

    @PostMapping("/{encounterId}/mark-consulted")
    public ResponseEntity<ApiResponse<EncounterResponse>> markConsulted(@PathVariable UUID encounterId) {
        UpdateEncounterRequest req = new UpdateEncounterRequest(null, EncounterStatus.BILLING_DONE, null);
        return ResponseEntity.ok(ApiResponse.ok("Encounter marked as consulted",
                encounterSvc.updateEncounter(encounterId, req)));
    }

    // ── Admission Referral ────────────────────────────────────────────────────

    @PostMapping("/{encounterId}/admit")
    public ResponseEntity<ApiResponse<EncounterResponse>> requestAdmission(
            @PathVariable UUID encounterId,
            @RequestBody AdmissionReferralRequest req) {
        Map<String, Object> data = new HashMap<>();
        data.put("admissionReason",     req.reason());
        data.put("adviceToPatient",     req.adviceToPatient());
        data.put("instructionsToNurses", req.instructionsToNurses());
        if (req.admissionDate() != null) data.put("requestedAdmissionDate", req.admissionDate().toString());
        data.put("status", "REQUESTED");
        encounterSvc.updateConsultantShare(encounterId, "ADMISSION_REQUEST", data);
        return ResponseEntity.ok(ApiResponse.ok("Admission requested", encounterSvc.findById(encounterId)));
    }

    // ── Prescription (OP inline) ─────────────────────────────────────────────

    @PostMapping("/{encounterId}/prescription")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> addPrescription(
            @PathVariable UUID encounterId,
            @Valid @RequestBody AddPrescriptionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Prescription saved", encounterSvc.addPrescription(encounterId, req)));
    }

    @GetMapping("/{encounterId}/prescription")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> listPrescriptions(
            @PathVariable UUID encounterId) {
        EncounterResponse enc = encounterSvc.findById(encounterId);
        return ResponseEntity.ok(ApiResponse.ok("OK", extractPrescriptions(enc)));
    }

    // ── Diagnostic Order (OP inline) ─────────────────────────────────────────

    @PostMapping("/{encounterId}/diagnostic-order")
    public ResponseEntity<ApiResponse<VisitDiagnosticOrderResponse>> addDiagnosticOrder(
            @PathVariable UUID encounterId,
            @Valid @RequestBody AddDiagnosticOrderRequest req) {
        VisitDiagnosticOrderResponse saved = encounterSvc.addDiagnosticOrder(encounterId, req);
        // Also push to Diagnostics module so it appears in the Diagnostics worklist
        try {
            com.hms.api.encounter.response.EncounterResponse enc = encounterSvc.findById(encounterId);
            if (enc != null && enc.patientId() != null && !req.items().isEmpty()) {
                var labLines = req.items().stream()
                    .filter(i -> !"RADIOLOGY".equalsIgnoreCase(i.category()))
                    .map(i -> new PlaceOrderRequest.OrderLineRequest(
                        i.diagnosticTestId() != null ? parseUUID(i.diagnosticTestId()) : java.util.UUID.randomUUID(),
                        i.testName(), null, null))
                    .toList();
                var radioLines = req.items().stream()
                    .filter(i -> "RADIOLOGY".equalsIgnoreCase(i.category()))
                    .map(i -> new PlaceOrderRequest.OrderLineRequest(
                        i.diagnosticTestId() != null ? parseUUID(i.diagnosticTestId()) : java.util.UUID.randomUUID(),
                        i.testName(), null, null))
                    .toList();
                if (!labLines.isEmpty())
                    diagnosticSvc.placeOrder(new PlaceOrderRequest(encounterId, enc.patientId(),
                        req.requestedById(), DiagnosticType.LAB, null, labLines));
                if (!radioLines.isEmpty())
                    diagnosticSvc.placeOrder(new PlaceOrderRequest(encounterId, enc.patientId(),
                        req.requestedById(), DiagnosticType.RADIOLOGY, null, radioLines));
            }
        } catch (Exception ex) {
            // Non-critical: log and continue; clinical order already saved
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Diagnostic order saved", saved));
    }

    @GetMapping("/{encounterId}/diagnostic-order")
    public ResponseEntity<ApiResponse<List<VisitDiagnosticOrderResponse>>> listDiagnosticOrders(
            @PathVariable UUID encounterId) {
        EncounterResponse enc = encounterSvc.findById(encounterId);
        return ResponseEntity.ok(ApiResponse.ok("OK", extractDiagOrders(enc)));
    }

    public record CasesheetLoadResponse(
            CaseSheetTemplateDetail template,
            List<CaseSheetRecordResponse> records) {}

    // ── Private helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<PrescriptionResponse> extractPrescriptions(EncounterResponse enc) {
        if (enc.consultantShareMap() == null) return List.of();
        Object raw = enc.consultantShareMap().get("prescriptions");
        if (raw instanceof List<?> list) {
            List<PrescriptionResponse> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?,?> m) {
                    Map<String, Object> mm = (Map<String, Object>) m;
                    List<PrescriptionResponse.PrescriptionLineResponse> lines = new ArrayList<>();
                    if (mm.get("items") instanceof List<?> rawItems) {
                        for (Object ri : rawItems) {
                            if (ri instanceof Map<?,?> lm) {
                                Map<String, Object> l = (Map<String, Object>) lm;
                                lines.add(new PrescriptionResponse.PrescriptionLineResponse(
                                    parseUUID(l.get("id")), str(l.get("drugItemId")), str(l.get("drugName")),
                                    str(l.get("frequency")), str(l.get("duration")),
                                    l.get("qty") instanceof Number n ? n.intValue() : 1,
                                    str(l.get("instructionId")), str(l.get("instructionLabel")),
                                    str(l.get("routeId")), str(l.get("routeLabel")), str(l.get("remarks"))
                                ));
                            }
                        }
                    }
                    result.add(new PrescriptionResponse(
                        parseUUID(mm.get("id")), parseUUID(mm.get("encounterId")),
                        parseUUID(mm.get("requestedById")), str(mm.get("requestedByName")),
                        parseInstant(mm.get("createdAt")), lines
                    ));
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<VisitDiagnosticOrderResponse> extractDiagOrders(EncounterResponse enc) {
        if (enc.consultantShareMap() == null) return List.of();
        Object raw = enc.consultantShareMap().get("diagnostic_orders");
        if (raw instanceof List<?> list) {
            List<VisitDiagnosticOrderResponse> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?,?> m) {
                    Map<String, Object> mm = (Map<String, Object>) m;
                    List<VisitDiagnosticOrderResponse.DiagnosticOrderLineResponse> lines = new ArrayList<>();
                    if (mm.get("items") instanceof List<?> rawItems) {
                        for (Object ri : rawItems) {
                            if (ri instanceof Map<?,?> lm) {
                                Map<String, Object> l = (Map<String, Object>) lm;
                                lines.add(new VisitDiagnosticOrderResponse.DiagnosticOrderLineResponse(
                                    parseUUID(l.get("id")), str(l.get("diagnosticTestId")),
                                    str(l.get("testName")), str(l.get("category")),
                                    str(l.get("status")) != null ? str(l.get("status")) : "ORDERED"
                                ));
                            }
                        }
                    }
                    result.add(new VisitDiagnosticOrderResponse(
                        parseUUID(mm.get("id")), parseUUID(mm.get("encounterId")),
                        parseUUID(mm.get("requestedById")), str(mm.get("requestedByName")),
                        parseInstant(mm.get("orderedAt")), lines
                    ));
                }
            }
            return result;
        }
        return List.of();
    }

    private static UUID parseUUID(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (Exception e) { return null; }
    }
    private static java.time.Instant parseInstant(Object o) {
        if (o == null) return null;
        try { return java.time.Instant.parse(o.toString()); } catch (Exception e) { return null; }
    }
    private static String str(Object o) { return o == null ? null : o.toString(); }
}
