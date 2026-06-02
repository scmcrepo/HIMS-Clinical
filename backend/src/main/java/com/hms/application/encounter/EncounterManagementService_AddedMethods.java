package com.hms.application.encounter;

// ─────────────────────────────────────────────────────────────────────────────
// PATCH — add these methods inside EncounterManagementService class body
// (before the final closing brace).
// ─────────────────────────────────────────────────────────────────────────────

import com.hms.api.opip.request.*;
import com.hms.api.opip.response.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * === APPEND THESE METHODS INSIDE EncounterManagementService ===
 *
 * All clinical sub-resources (prescriptions, diagnostic orders, progress notes,
 * nurse notes, other charges, IP vital-history) are stored as JSON arrays
 * inside the encounter's consultantShareMap / vitalData columns.
 * This avoids new DB tables for the initial implementation while preserving
 * the full data model for future migration to proper entities.
 */

// ── IP Vitals (append-only list) ─────────────────────────────────────────────

/*
@Transactional
public EncounterResponse appendIpVitals(UUID id, RecordVitalsRequest req) {
    ClinicalEncounter e = findOrThrow(id);
    if (e.getVitalData() == null) e.setVitalData(new HashMap<>());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> history =
        (List<Map<String, Object>>) e.getVitalData()
            .computeIfAbsent("vitals_history", k -> new ArrayList<>());

    Map<String, Object> entry = new HashMap<>(req.vitals());
    entry.put("id",        UUID.randomUUID().toString());
    entry.put("recordedAt", Instant.now().toString());
    history.add(entry);

    // Also transition OP status on first vitals (no-op for IP where status != CHECKED_IN)
    if (e.getEncounterStatus() == EncounterStatus.CHECKED_IN) {
        e.updateStatus(EncounterStatus.CONSULTATION_STARTED);
    }

    ClinicalEncounter saved = encounterRepo.save(e);
    return encounterMapper.toResponse(saved,
        resolvePatientName(saved.getPatientId()),
        resolvePatientNumber(saved.getPatientId()));
}
*/

// ── Prescription ──────────────────────────────────────────────────────────────

/*
@Transactional
public PrescriptionResponse addPrescription(UUID encounterId, AddPrescriptionRequest req) {
    ClinicalEncounter e = findOrThrow(encounterId);
    if (e.getConsultantShareMap() == null) e.setConsultantShareMap(new HashMap<>());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> prescriptions =
        (List<Map<String, Object>>) e.getConsultantShareMap()
            .computeIfAbsent("prescriptions", k -> new ArrayList<>());

    String requestedByName = resolveConsultantName(req.requestedById());
    UUID   prescriptionId  = UUID.randomUUID();
    Instant now            = Instant.now();

    List<Map<String, Object>> lineItems = new ArrayList<>();
    for (AddPrescriptionRequest.PrescriptionLineRequest line : req.items()) {
        Map<String, Object> l = new HashMap<>();
        l.put("id",               UUID.randomUUID().toString());
        l.put("drugItemId",       line.drugItemId());
        l.put("drugName",         line.drugName());
        l.put("frequency",        line.frequency());
        l.put("duration",         line.duration());
        l.put("qty",              line.qty());
        l.put("instructionId",    line.instructionId());
        l.put("instructionLabel", line.instructionLabel());
        l.put("routeId",          line.routeId());
        l.put("routeLabel",       line.routeLabel());
        l.put("remarks",          line.remarks());
        lineItems.add(l);
    }

    Map<String, Object> rx = new HashMap<>();
    rx.put("id",                prescriptionId.toString());
    rx.put("encounterId",       encounterId.toString());
    rx.put("requestedById",     req.requestedById() != null ? req.requestedById().toString() : null);
    rx.put("requestedByName",   requestedByName);
    rx.put("createdAt",         now.toString());
    rx.put("items",             lineItems);
    prescriptions.add(rx);

    encounterRepo.save(e);

    // Build response
    List<PrescriptionResponse.PrescriptionLineResponse> responseLines = req.items().stream()
        .map(l -> new PrescriptionResponse.PrescriptionLineResponse(
            UUID.randomUUID(), l.drugItemId(), l.drugName(), l.frequency(), l.duration(),
            l.qty(), l.instructionId(), l.instructionLabel(), l.routeId(), l.routeLabel(), l.remarks()
        )).toList();

    return new PrescriptionResponse(prescriptionId, encounterId,
        req.requestedById(), requestedByName, now, responseLines);
}
*/

// ── Diagnostic Order ──────────────────────────────────────────────────────────

/*
@Transactional
public VisitDiagnosticOrderResponse addDiagnosticOrder(UUID encounterId, AddDiagnosticOrderRequest req) {
    ClinicalEncounter e = findOrThrow(encounterId);
    if (e.getConsultantShareMap() == null) e.setConsultantShareMap(new HashMap<>());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> orders =
        (List<Map<String, Object>>) e.getConsultantShareMap()
            .computeIfAbsent("diagnostic_orders", k -> new ArrayList<>());

    String requestedByName = resolveConsultantName(req.requestedById());
    UUID   orderId         = UUID.randomUUID();
    Instant now            = Instant.now();

    List<Map<String, Object>> lineItems = new ArrayList<>();
    for (AddDiagnosticOrderRequest.DiagnosticOrderLineRequest line : req.items()) {
        Map<String, Object> l = new HashMap<>();
        l.put("id",               UUID.randomUUID().toString());
        l.put("diagnosticTestId", line.diagnosticTestId());
        l.put("testName",         line.testName());
        l.put("category",         line.category());
        l.put("status",           "ORDERED");
        lineItems.add(l);
    }

    Map<String, Object> order = new HashMap<>();
    order.put("id",              orderId.toString());
    order.put("encounterId",     encounterId.toString());
    order.put("requestedById",   req.requestedById() != null ? req.requestedById().toString() : null);
    order.put("requestedByName", requestedByName);
    order.put("orderedAt",       now.toString());
    order.put("items",           lineItems);
    orders.add(order);

    encounterRepo.save(e);

    List<VisitDiagnosticOrderResponse.DiagnosticOrderLineResponse> responseLines = req.items().stream()
        .map(l -> new VisitDiagnosticOrderResponse.DiagnosticOrderLineResponse(
            UUID.randomUUID(), l.diagnosticTestId(), l.testName(), l.category(), "ORDERED"
        )).toList();

    return new VisitDiagnosticOrderResponse(orderId, encounterId,
        req.requestedById(), requestedByName, now, responseLines);
}
*/

// ── Progress Notes & Nurse Notes (shared logic) ────────────────────────────────

/*
@Transactional
public ClinicalNoteResponse addClinicalNote(UUID encounterId, String notes,
        Instant noteAt, UUID requestedById, String noteKey) {
    ClinicalEncounter e = findOrThrow(encounterId);
    if (e.getConsultantShareMap() == null) e.setConsultantShareMap(new HashMap<>());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list =
        (List<Map<String, Object>>) e.getConsultantShareMap()
            .computeIfAbsent(noteKey, k -> new ArrayList<>());

    String requestedByName = resolveConsultantName(requestedById);
    UUID   noteId   = UUID.randomUUID();
    Instant now     = Instant.now();
    Instant at      = noteAt != null ? noteAt : now;

    Map<String, Object> entry = new HashMap<>();
    entry.put("id",                noteId.toString());
    entry.put("encounterId",       encounterId.toString());
    entry.put("notes",             notes);
    entry.put("noteAt",            at.toString());
    entry.put("requestedById",     requestedById != null ? requestedById.toString() : null);
    entry.put("requestedByName",   requestedByName);
    entry.put("createdAt",         now.toString());
    list.add(entry);

    encounterRepo.save(e);
    return new ClinicalNoteResponse(noteId, encounterId, notes, at,
        requestedById, requestedByName, now);
}
*/

// ── Other Charges ──────────────────────────────────────────────────────────────

/*
@Transactional
public OtherChargeResponse addOtherCharge(UUID encounterId, AddOtherChargeRequest req) {
    ClinicalEncounter e = findOrThrow(encounterId);
    if (e.getConsultantShareMap() == null) e.setConsultantShareMap(new HashMap<>());

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> charges =
        (List<Map<String, Object>>) e.getConsultantShareMap()
            .computeIfAbsent("other_charges", k -> new ArrayList<>());

    UUID    chargeId = UUID.randomUUID();
    Instant now      = Instant.now();

    Map<String, Object> entry = new HashMap<>();
    entry.put("id",                   chargeId.toString());
    entry.put("encounterId",          encounterId.toString());
    entry.put("chargeLabel",          req.chargeLabel());
    entry.put("serviceCatalogItemId", req.serviceCatalogItemId());
    entry.put("amount",               req.amount().doubleValue());
    entry.put("qty",                  req.qty() > 0 ? req.qty() : 1);
    entry.put("remarks",              req.remarks());
    entry.put("createdAt",            now.toString());
    charges.add(entry);

    encounterRepo.save(e);
    return new OtherChargeResponse(chargeId, encounterId, req.chargeLabel(),
        req.serviceCatalogItemId(), req.amount(),
        req.qty() > 0 ? req.qty() : 1, req.remarks(), now);
}
*/

// ── Private helper ─────────────────────────────────────────────────────────────

/*
private String resolveConsultantName(UUID consultantId) {
    if (consultantId == null) return null;
    return consultantRepo.findById(consultantId)
        .map(c -> (c.getSalutation() != null ? c.getSalutation() + " " : "")
                   + c.getFirstName() + " " + c.getLastName())
        .orElse(null);
}
*/
