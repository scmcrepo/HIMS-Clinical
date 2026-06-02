package com.hms.api.opip;

import com.hms.api.casesheet.request.SaveRecordRequest;
import com.hms.api.casesheet.response.CaseSheetRecordResponse;
import com.hms.api.casesheet.response.CaseSheetTemplateDetail;
import com.hms.api.encounter.request.DischargeRequest;
import com.hms.api.encounter.request.RecordVitalsRequest;
import com.hms.api.encounter.response.EncounterResponse;
import com.hms.api.encounter.response.EncounterSummaryResponse;
import com.hms.api.opip.request.*;
import com.hms.api.opip.response.*;
import com.hms.api.shared.ApiResponse;
import com.hms.application.casesheet.CaseSheetService;
import com.hms.application.encounter.DiagnosticBillingIntegrationHelper;
import com.hms.api.diagnostic.request.PlaceOrderRequest;
import com.hms.domain.diagnostic.model.DiagnosticType;
import com.hms.api.billing.request.AddChargeRequest;
import com.hms.application.encounter.EncounterManagementService;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.casesheet.model.CaseSheetVisitType;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IP CaseSheet Controller — inpatient clinical documentation.
 *
 * GET  /ip-casesheet                              — active inpatient list
 * GET  /ip-casesheet/{id}                         — encounter detail
 * GET  /ip-casesheet/{id}/casesheet               — load template + records
 * POST /ip-casesheet/{id}/casesheet               — save/update (partial merge)
 * POST /ip-casesheet/{id}/vitals                  — record vitals (multiple allowed)
 * GET  /ip-casesheet/{id}/vitals                  — list all vital entries
 * POST /ip-casesheet/{id}/prescription            — add prescription
 * GET  /ip-casesheet/{id}/prescription            — list all prescriptions
 * POST /ip-casesheet/{id}/diagnostic-order        — add diagnostic order
 * GET  /ip-casesheet/{id}/diagnostic-order        — list all diagnostic orders
 * POST /ip-casesheet/{id}/progress-notes          — add progress note
 * GET  /ip-casesheet/{id}/progress-notes          — list all progress notes
 * POST /ip-casesheet/{id}/nurse-notes             — add nurse note
 * GET  /ip-casesheet/{id}/nurse-notes             — list all nurse notes
 * POST /ip-casesheet/{id}/other-charges           — add billable charge
 * GET  /ip-casesheet/{id}/other-charges           — list all charges
 * POST /ip-casesheet/{id}/discharge               — discharge patient
 *
 * Quick-add lookup endpoints (shared with OP):
 * GET  /op-ip/favorites?consultantId=&type=       — consultant favorites (DRUG|TEST)
 * GET  /op-ip/frequently-used?consultantId=&type= — top-N frequently used items
 * GET  /op-ip/last-prescribed?encounterId=        — last prescribed drugs for patient
 */
@RestController
@RequestMapping("/ip-casesheet")
@RequiredArgsConstructor
@Slf4j
public class InpatientCaseSheetController {

    private final EncounterManagementService encounterSvc;
    private final CaseSheetService           casesheetSvc;
    private final DiagnosticBillingIntegrationHelper integrationHelper;

    // ── List / Detail ─────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<Page<EncounterSummaryResponse>>> getActiveInpatients(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable p = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterSvc.findActiveInpatients(query, p)));
    }

    @GetMapping("/{encounterId}")
    public ResponseEntity<ApiResponse<EncounterResponse>> getEncounter(@PathVariable UUID encounterId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", encounterSvc.findById(encounterId)));
    }

    // ── CaseSheet ─────────────────────────────────────────────────────────────

    @GetMapping("/{encounterId}/casesheet")
    public ResponseEntity<ApiResponse<IpCasesheetLoadResponse>> loadCasesheet(
            @PathVariable UUID encounterId,
            @RequestParam(defaultValue = "GENERAL") String specialization) {

        List<CaseSheetRecordResponse> records = casesheetSvc.getRecordsByEncounter(encounterId);

        CaseSheetTemplateDetail template = null;
        if (!records.isEmpty()) {
            template = casesheetSvc.getTemplate(records.get(0).template().id());
        } else {
            try {
                String spec = specialization;
                if (spec == null || spec.isBlank() || "GENERAL".equalsIgnoreCase(spec) || "ORTHOPAEDICS".equalsIgnoreCase(spec)) {
                    spec = casesheetSvc.getSpecializationForEncounter(encounterId);
                }
                template = casesheetSvc.getDefaultTemplate(spec, CaseSheetVisitType.IP);
            } catch (Exception ignored) { /* no default — UI shows template picker */ }
        }
        return ResponseEntity.ok(ApiResponse.ok("OK", new IpCasesheetLoadResponse(template, records)));
    }

    @PostMapping("/{encounterId}/casesheet")
    public ResponseEntity<ApiResponse<CaseSheetRecordResponse>> saveCasesheet(
            @PathVariable UUID encounterId,
            @Valid @RequestBody SaveRecordRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("IP casesheet saved", casesheetSvc.saveRecord(encounterId, req)));
    }

    // ── Vital Signs (IP allows multiple entries) ───────────────────────────────

    /**
     * List all recorded vital sign entries for this encounter.
     * Stored as a JSON array under vitalData["vitals_history"].
     */
    @GetMapping("/{encounterId}/vitals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVitals(
            @PathVariable UUID encounterId) {
        EncounterResponse enc = encounterSvc.findById(encounterId);
        List<Map<String, Object>> history = extractVitalsHistory(enc.vitalData());
        return ResponseEntity.ok(ApiResponse.ok("OK", history));
    }

    /**
     * Record a new vital signs entry for this IP encounter.
     * Each call appends to the vitals history list — does NOT overwrite.
     */
    @PostMapping("/{encounterId}/vitals")
    public ResponseEntity<ApiResponse<EncounterResponse>> recordVitals(
            @PathVariable UUID encounterId,
            @Valid @RequestBody RecordVitalsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Vitals recorded",
                encounterSvc.appendIpVitals(encounterId, req)));
    }

    // ── Prescription ──────────────────────────────────────────────────────────

    /**
     * List all prescriptions for this IP encounter.
     * Stored under consultantShareMap["prescriptions"] as a JSON array.
     */
    @GetMapping("/{encounterId}/prescription")
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> listPrescriptions(
            @PathVariable UUID encounterId) {
        EncounterResponse enc = encounterSvc.findById(encounterId);
        List<PrescriptionResponse> list = extractPrescriptions(enc);
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    /**
     * Add a new prescription to this IP encounter.
     */
    @PostMapping("/{encounterId}/prescription")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> addPrescription(
            @PathVariable UUID encounterId,
            @Valid @RequestBody AddPrescriptionRequest req) {
        PrescriptionResponse saved = encounterSvc.addPrescription(encounterId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Prescription added", saved));
    }

    // ── Diagnostic Order ──────────────────────────────────────────────────────

    /**
     * List all diagnostic orders for this IP encounter.
     */
    @GetMapping("/{encounterId}/diagnostic-order")
    public ResponseEntity<ApiResponse<List<VisitDiagnosticOrderResponse>>> listDiagnosticOrders(
            @PathVariable UUID encounterId) {
        EncounterResponse enc = encounterSvc.findById(encounterId);
        List<VisitDiagnosticOrderResponse> list = extractDiagnosticOrders(enc);
        return ResponseEntity.ok(ApiResponse.ok("OK", list));
    }

    /**
     * Add a new diagnostic order to this IP encounter.
     * Per spec: for IP, diagnostic orders are automatically pushed as charges
     * to the running draft bill immediately upon creation.
     */
    @PostMapping("/{encounterId}/diagnostic-order")
    public ResponseEntity<ApiResponse<VisitDiagnosticOrderResponse>> addDiagnosticOrder(
            @PathVariable UUID encounterId,
            @Valid @RequestBody AddDiagnosticOrderRequest req) {
        VisitDiagnosticOrderResponse saved = encounterSvc.addDiagnosticOrder(encounterId, req);
        
        // Push to Diagnostics module + auto-charge via helper in isolated transaction
        try {
            com.hms.api.encounter.response.EncounterResponse enc = encounterSvc.findById(encounterId);
            if (enc != null && enc.patientId() != null) {
                integrationHelper.placeDiagnosticOrderAndBill(
                    req.items(), enc.patientId(), encounterId, EncounterType.INPATIENT, req.requestedById()
                );
            }
        } catch (Exception ex) {
            log.warn("Failed to link diagnostic order to billing/diagnostics for encounter {}: {}", encounterId, ex.getMessage());
        }
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Diagnostic order added", saved));
    }

    // ── Progress Notes ────────────────────────────────────────────────────────

    @GetMapping("/{encounterId}/progress-notes")
    public ResponseEntity<ApiResponse<List<ClinicalNoteResponse>>> listProgressNotes(
            @PathVariable UUID encounterId) {
        EncounterResponse enc = encounterSvc.findById(encounterId);
        List<ClinicalNoteResponse> notes = extractNotes(enc, "progress_notes");
        return ResponseEntity.ok(ApiResponse.ok("OK", notes));
    }

    @PostMapping("/{encounterId}/progress-notes")
    public ResponseEntity<ApiResponse<ClinicalNoteResponse>> addProgressNote(
            @PathVariable UUID encounterId,
            @Valid @RequestBody AddProgressNoteRequest req) {
        ClinicalNoteResponse saved = encounterSvc.addClinicalNote(encounterId, req.notes(),
                req.noteAt(), req.requestedById(), "progress_notes");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Progress note added", saved));
    }

    // ── Nurse Notes ───────────────────────────────────────────────────────────

    @GetMapping("/{encounterId}/nurse-notes")
    public ResponseEntity<ApiResponse<List<ClinicalNoteResponse>>> listNurseNotes(
            @PathVariable UUID encounterId) {
        EncounterResponse enc = encounterSvc.findById(encounterId);
        List<ClinicalNoteResponse> notes = extractNotes(enc, "nurse_notes");
        return ResponseEntity.ok(ApiResponse.ok("OK", notes));
    }

    @PostMapping("/{encounterId}/nurse-notes")
    public ResponseEntity<ApiResponse<ClinicalNoteResponse>> addNurseNote(
            @PathVariable UUID encounterId,
            @Valid @RequestBody AddNurseNoteRequest req) {
        ClinicalNoteResponse saved = encounterSvc.addClinicalNote(encounterId, req.notes(),
                req.noteAt(), req.requestedById(), "nurse_notes");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Nurse note added", saved));
    }

    // ── Other Charges ─────────────────────────────────────────────────────────

    @GetMapping("/{encounterId}/other-charges")
    public ResponseEntity<ApiResponse<List<OtherChargeResponse>>> listOtherCharges(
            @PathVariable UUID encounterId) {
        EncounterResponse enc = encounterSvc.findById(encounterId);
        List<OtherChargeResponse> charges = extractOtherCharges(enc);
        return ResponseEntity.ok(ApiResponse.ok("OK", charges));
    }

    @PostMapping("/{encounterId}/other-charges")
    public ResponseEntity<ApiResponse<OtherChargeResponse>> addOtherCharge(
            @PathVariable UUID encounterId,
            @Valid @RequestBody AddOtherChargeRequest req) {
        OtherChargeResponse saved = encounterSvc.addOtherCharge(encounterId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Charge added", saved));
    }

    // ── Discharge ─────────────────────────────────────────────────────────────

    @PostMapping("/{encounterId}/discharge")
    public ResponseEntity<ApiResponse<EncounterResponse>> discharge(
            @PathVariable UUID encounterId,
            @RequestBody DischargeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Patient discharged",
                encounterSvc.discharge(encounterId, req)));
    }

    // ── Response Records ──────────────────────────────────────────────────────

    public record IpCasesheetLoadResponse(
            CaseSheetTemplateDetail template,
            List<CaseSheetRecordResponse> records) {}

    // ── Private helpers — extract data stored in vitalData / consultantShareMap ─

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractVitalsHistory(Map<String, Object> vitalData) {
        if (vitalData == null) return List.of();
        Object raw = vitalData.get("vitals_history");
        if (raw instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<PrescriptionResponse> extractPrescriptions(EncounterResponse enc) {
        if (enc.consultantShareMap() == null) return List.of();
        Object raw = enc.consultantShareMap().get("prescriptions");
        if (raw instanceof List<?> list) {
            List<PrescriptionResponse> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?,?> m) {
                    result.add(mapToPrescription((Map<String, Object>) m));
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<VisitDiagnosticOrderResponse> extractDiagnosticOrders(EncounterResponse enc) {
        if (enc.consultantShareMap() == null) return List.of();
        Object raw = enc.consultantShareMap().get("diagnostic_orders");
        if (raw instanceof List<?> list) {
            List<VisitDiagnosticOrderResponse> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?,?> m) {
                    result.add(mapToDiagOrder((Map<String, Object>) m));
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<ClinicalNoteResponse> extractNotes(EncounterResponse enc, String key) {
        if (enc.consultantShareMap() == null) return List.of();
        Object raw = enc.consultantShareMap().get(key);
        if (raw instanceof List<?> list) {
            List<ClinicalNoteResponse> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?,?> m) {
                    Map<String, Object> mm = (Map<String, Object>) m;
                    result.add(new ClinicalNoteResponse(
                        parseUUID(mm.get("id")),
                        parseUUID(mm.get("encounterId")),
                        str(mm.get("notes")),
                        parseInstant(mm.get("noteAt")),
                        parseUUID(mm.get("requestedById")),
                        str(mm.get("requestedByName")),
                        parseInstant(mm.get("createdAt"))
                    ));
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<OtherChargeResponse> extractOtherCharges(EncounterResponse enc) {
        if (enc.consultantShareMap() == null) return List.of();
        Object raw = enc.consultantShareMap().get("other_charges");
        if (raw instanceof List<?> list) {
            List<OtherChargeResponse> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?,?> m) {
                    Map<String, Object> mm = (Map<String, Object>) m;
                    result.add(new OtherChargeResponse(
                        parseUUID(mm.get("id")),
                        parseUUID(mm.get("encounterId")),
                        str(mm.get("chargeLabel")),
                        str(mm.get("serviceCatalogItemId")),
                        mm.get("amount") instanceof Number n
                            ? java.math.BigDecimal.valueOf(n.doubleValue()) : java.math.BigDecimal.ZERO,
                        mm.get("qty") instanceof Number n2 ? n2.intValue() : 1,
                        str(mm.get("remarks")),
                        parseInstant(mm.get("createdAt"))
                    ));
                }
            }
            return result;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private PrescriptionResponse mapToPrescription(Map<String, Object> m) {
        List<PrescriptionResponse.PrescriptionLineResponse> items = new ArrayList<>();
        if (m.get("items") instanceof List<?> rawItems) {
            for (Object ri : rawItems) {
                if (ri instanceof Map<?,?> lm) {
                    Map<String, Object> l = (Map<String, Object>) lm;
                    items.add(new PrescriptionResponse.PrescriptionLineResponse(
                        parseUUID(l.get("id")), str(l.get("drugItemId")), str(l.get("drugName")),
                        str(l.get("frequency")), str(l.get("duration")),
                        l.get("qty") instanceof Number n ? n.intValue() : 1,
                        str(l.get("instructionId")), str(l.get("instructionLabel")),
                        str(l.get("routeId")), str(l.get("routeLabel")), str(l.get("remarks"))
                    ));
                }
            }
        }
        return new PrescriptionResponse(
            parseUUID(m.get("id")), parseUUID(m.get("encounterId")),
            parseUUID(m.get("requestedById")), str(m.get("requestedByName")),
            parseInstant(m.get("createdAt")), items
        );
    }

    @SuppressWarnings("unchecked")
    private VisitDiagnosticOrderResponse mapToDiagOrder(Map<String, Object> m) {
        List<VisitDiagnosticOrderResponse.DiagnosticOrderLineResponse> items = new ArrayList<>();
        if (m.get("items") instanceof List<?> rawItems) {
            for (Object ri : rawItems) {
                if (ri instanceof Map<?,?> lm) {
                    Map<String, Object> l = (Map<String, Object>) lm;
                    items.add(new VisitDiagnosticOrderResponse.DiagnosticOrderLineResponse(
                        parseUUID(l.get("id")), str(l.get("diagnosticTestId")),
                        str(l.get("testName")), str(l.get("category")),
                        str(l.get("status")) != null ? str(l.get("status")) : "ORDERED"
                    ));
                }
            }
        }
        return new VisitDiagnosticOrderResponse(
            parseUUID(m.get("id")), parseUUID(m.get("encounterId")),
            parseUUID(m.get("requestedById")), str(m.get("requestedByName")),
            parseInstant(m.get("orderedAt")), items
        );
    }

    private static UUID parseUUID(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(o.toString()); } catch (Exception e) { return null; }
    }
    private static Instant parseInstant(Object o) {
        if (o == null) return null;
        try { return Instant.parse(o.toString()); } catch (Exception e) { return null; }
    }
    private static String str(Object o) { return o == null ? null : o.toString(); }
}
