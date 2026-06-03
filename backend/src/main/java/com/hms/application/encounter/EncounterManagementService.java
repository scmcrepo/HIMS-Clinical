package com.hms.application.encounter;

import com.hms.api.encounter.request.*;
import com.hms.api.encounter.response.EncounterResponse;
import com.hms.api.encounter.response.EncounterSummaryResponse;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.encounter.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.EncounterMapper;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j
public class EncounterManagementService {

    private final ClinicalEncounterJpaRepository encounterRepo;
    private final EncounterMapper encounterMapper;
    private final com.hms.infrastructure.persistence.patient.PatientJpaRepository patientRepo;
    private final com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository consultantRepo;
    private final NumberSequenceJpaRepository numberSequenceRepo;
    private final com.hms.infrastructure.persistence.billing.BillJpaRepository billRepo;
    private final com.hms.application.billing.BillingOperationsService billingService;
    private final com.hms.application.bed.BedManagementService bedService;
    private final org.springframework.context.ApplicationContext applicationContext;

    private String resolvePatientName(UUID patientId) {
        if (patientId == null) return "Unknown Patient";
        return patientRepo.findById(patientId)
            .map(p -> p.getFirstName() + " " + p.getLastName())
            .orElse("Unknown Patient");
    }

    private String resolvePatientNumber(UUID patientId) {
        if (patientId == null) return "NEW";
        return numberSequenceRepo.findById(patientId)
            .map(NumberSequenceEntity::getValue)
            .orElse("NEW");
    }

    private String resolvePatientMobile(UUID patientId) {
        if (patientId == null) return "—";
        return patientRepo.findById(patientId)
            .map(p -> p.getContactNumber())
            .orElse("—");
    }

    @Transactional(readOnly = true)
    public Page<EncounterSummaryResponse> findAll(String query, String date, Pageable pageable) {
        Instant start = null;
        Instant end = null;
        if (date != null && !date.isBlank()) {
            try {
                java.time.LocalDate localDate = java.time.LocalDate.parse(date);
                start = localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                end = localDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (start != null && end != null) {
            if (query != null && !query.isBlank()) {
                return encounterRepo.searchAllWithDate(query, start, end, pageable).map(this::mapWithNames);
            }
            return encounterRepo.findAllWithDate(start, end, pageable).map(this::mapWithNames);
        }

        if (query != null && !query.isBlank()) {
            return encounterRepo.searchAll(query, pageable).map(this::mapWithNames);
        }
        return encounterRepo.findAll(pageable).map(this::mapWithNames);
    }

    @Transactional
    public EncounterResponse createOutpatientEncounter(CreateEncounterRequest req) {
        // Strict rule: No new encounter if draft bills exist (unless they are from today)
        List<com.hms.domain.billing.model.Bill> draftBills = billRepo.findDraftBillsByPatientId(req.patientId());
        if (!draftBills.isEmpty()) {
            boolean hasOldDraft = draftBills.stream()
                .anyMatch(b -> {
                    java.time.Instant createdAt = b.getCreatedAt();
                    if (createdAt == null) return true;
                    return createdAt.isBefore(java.time.Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS));
                });
            if (hasOldDraft) {
                throw new BusinessRuleViolationException("Billing is pending for this patient. Please settle existing draft bills before creating a new encounter.");
            }
        }

        ClinicalEncounter e = new ClinicalEncounter();
        e.setPatientId(req.patientId());
        e.setPrimaryProviderId(req.primaryProviderId());
        e.setEncounterType(EncounterType.OUTPATIENT);
        e.setVisitMode(req.visitMode() != null ? req.visitMode() : VisitMode.WALK_IN);
        e.setStartedAt(Instant.now());
        e.setCheckedInAt(LocalTime.now());
        e.setAppointmentId(req.appointmentId());
        e.setHasDraftBill(false);
        ClinicalEncounter saved = encounterRepo.save(e);
        return encounterMapper.toResponse(saved, resolvePatientName(saved.getPatientId()), resolvePatientNumber(saved.getPatientId()));
    }

    @Transactional
    public EncounterResponse createInpatientEncounter(CreateEncounterRequest req) {
        // Strict rule: No new encounter if draft bills exist
        if (!billRepo.findDraftBillsByPatientId(req.patientId()).isEmpty()) {
            throw new BusinessRuleViolationException("Billing is pending for this patient. Please settle existing draft bills before creating a new encounter.");
        }

        encounterRepo.findActiveInpatientByPatientId(req.patientId()).stream().findFirst().ifPresent(ex -> {
            throw new BusinessRuleViolationException("Patient already has active inpatient encounter: " + ex.getId());
        });
        ClinicalEncounter e = new ClinicalEncounter();
        e.setPatientId(req.patientId());
        e.setPrimaryProviderId(req.primaryProviderId());
        e.setEncounterType(EncounterType.INPATIENT);
        e.setVisitMode(VisitMode.APPOINTMENT);
        e.setStartedAt(Instant.now());
        e.setHasDraftBill(false);
        ClinicalEncounter saved = encounterRepo.save(e);

        return encounterMapper.toResponse(saved, resolvePatientName(saved.getPatientId()), resolvePatientNumber(saved.getPatientId()));
    }

    @Transactional(readOnly = true)
    public EncounterResponse findById(UUID id) { 
        ClinicalEncounter e = findOrThrow(id);
        return encounterMapper.toResponse(e, resolvePatientName(e.getPatientId()), resolvePatientNumber(e.getPatientId())); 
    }

    @Transactional(readOnly = true)
    public Page<EncounterSummaryResponse> findByPatient(UUID patientId, Pageable pageable) {
        return encounterRepo.findByPatientIdPaged(patientId, pageable).map(this::mapWithNames);
    }

    private EncounterSummaryResponse mapWithNames(ClinicalEncounter e) {
        log.info("Mapping encounter: id={}, patientId={}", e.getId(), e.getPatientId());
        try {
            java.util.Optional<com.hms.domain.patient.model.Patient> patientOpt = e.getPatientId() != null 
                ? patientRepo.findById(e.getPatientId()) 
                : java.util.Optional.empty();
            String pName = patientOpt.map(p -> p.getFirstName() + " " + p.getLastName()).orElse("Unknown Patient");
            String pNum = resolvePatientNumber(e.getPatientId());
            String pMobile = patientOpt.map(p -> p.getContactNumber()).orElse("—");
            String pGender = patientOpt.map(p -> p.getGender() != null ? p.getGender().name() : null).orElse(null);
            String pAge = patientOpt.map(p -> p.computeAge()).orElse("—");
            
            String dName = "Staff";
            if (e.getPrimaryProviderId() != null) {
                dName = consultantRepo.findById(e.getPrimaryProviderId())
                    .map(c -> (c.getSalutation() != null ? c.getSalutation() + " " : "") + c.getFirstName() + " " + c.getLastName())
                    .orElse("Staff");
            }
            
            String bedName = bedService.getActiveBedNameForEncounter(e.getId());
            
            EncounterSummaryResponse res = encounterMapper.toSummaryResponse(e, pName, pNum, pMobile, pGender, pAge);
            return new EncounterSummaryResponse(
                res.id(), res.patientId(), res.patientNumber(), res.patientName(), res.patientMobileNumber(),
                res.patientGender(), res.patientAge(),
                res.primaryProviderId(), dName,
                res.encounterType(), res.status(),
                res.startedAt(), res.dischargedAt(),
                res.diagnosis(), e.isHasBed(), e.isHasDraftBill(),
                bedName
            );
        } catch (Exception ex) {
            log.error("Error mapping encounter {}: {}", e.getId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public EncounterResponse getActiveInpatient(UUID patientId) {
        ClinicalEncounter e = encounterRepo.findActiveInpatientByPatientId(patientId).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("No active inpatient for patient: " + patientId));
        return encounterMapper.toResponse(e, resolvePatientName(e.getPatientId()), resolvePatientNumber(e.getPatientId()));
    }

    @Transactional(readOnly = true)
    public Page<EncounterSummaryResponse> findActiveInpatients(String query, Pageable pageable) {
        return findActiveInpatients(query, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<EncounterSummaryResponse> findActiveInpatients(String query, String date, UUID consultantId, Pageable pageable) {
        Instant start = null;
        Instant end = null;
        boolean dateSpecified = false;
        if (date != null && !date.isBlank()) {
            try {
                java.time.LocalDate localDate = java.time.LocalDate.parse(date);
                start = localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                end = localDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                dateSpecified = true;
            } catch (Exception ex) {
                log.warn("Invalid date format passed to findActiveInpatients: {}", date);
            }
        }

        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;

        return encounterRepo.searchInpatientsFiltered(cleanQuery, consultantId, dateSpecified, start, end, pageable)
                .map(this::mapWithNames);
    }

    @Transactional(readOnly = true)
    public List<EncounterSummaryResponse> findActiveInpatientsWithBeds() {
        return encounterRepo.findActiveInpatientsPaged(Pageable.unpaged())
            .map(this::mapWithNames)
            .getContent();
    }

    @Transactional(readOnly = true)
    public Page<EncounterSummaryResponse> findTodayOutpatients(String query, String date, Pageable pageable) {
        Instant start = null;
        Instant end = null;
        if (date != null && !date.isBlank()) {
            try {
                java.time.LocalDate localDate = java.time.LocalDate.parse(date);
                start = localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
                end = localDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (start == null || end == null) {
            start = java.time.Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS);
            end = java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS);
        }

        if (query != null && !query.isBlank()) {
            return encounterRepo.searchOutpatientsByDate(query, start, end, pageable).map(this::mapWithNames);
        }
        return encounterRepo.findOutpatientsByDate(start, end, pageable).map(this::mapWithNames);
    }

    @Transactional
    public EncounterResponse updateEncounter(UUID id, UpdateEncounterRequest req) {
        ClinicalEncounter e = findOrThrow(id);
        if (req.diagnosis()       != null) e.setDiagnosis(req.diagnosis());

        if (req.status()          != null) e.updateStatus(req.status());
        if (req.vitalData()       != null) e.setVitalData(req.vitalData());
        ClinicalEncounter saved = encounterRepo.save(e);
        return encounterMapper.toResponse(saved, resolvePatientName(saved.getPatientId()), resolvePatientNumber(saved.getPatientId()));
    }

    @Transactional
    public EncounterResponse recordVitals(UUID id, RecordVitalsRequest req) {
        ClinicalEncounter e = findOrThrow(id);
        if (e.getVitalData() == null) e.setVitalData(new HashMap<>());
        e.getVitalData().putAll(req.vitals());
        if (e.getEncounterStatus() == EncounterStatus.CHECKED_IN) e.updateStatus(EncounterStatus.CONSULTATION_STARTED);
        ClinicalEncounter saved = encounterRepo.save(e);
        return encounterMapper.toResponse(saved, resolvePatientName(saved.getPatientId()), resolvePatientNumber(saved.getPatientId()));
    }

    @Transactional
    public EncounterResponse recordCasesheet(UUID id, RecordCasesheetRequest req) {
        ClinicalEncounter e = findOrThrow(id);
        Map<String, Object> cs = new HashMap<>();
        if (req.chiefComplaint()           != null) cs.put("chiefComplaint", req.chiefComplaint());
        if (req.historyOfPresentIllness()  != null) cs.put("historyOfPresentIllness", req.historyOfPresentIllness());
        if (req.examination()              != null) cs.put("examination", req.examination());
        if (req.plan()                     != null) cs.put("plan", req.plan());
        if (req.customFields()             != null) cs.putAll(req.customFields());
        if (e.getVitalData() == null) e.setVitalData(new HashMap<>());
        e.getVitalData().put("casesheet", cs);
        if (req.diagnosis()       != null) e.setDiagnosis(req.diagnosis());

        e.recordCasesheetTimestamp();
        ClinicalEncounter saved = encounterRepo.save(e);
        return encounterMapper.toResponse(saved, resolvePatientName(saved.getPatientId()), resolvePatientNumber(saved.getPatientId()));
    }

    @Transactional
    public void recordCasesheetTimestamp(UUID id) {
        ClinicalEncounter e = findOrThrow(id); e.recordCasesheetTimestamp(); encounterRepo.save(e);
    }

    @Transactional
    public EncounterResponse discharge(UUID id, DischargeRequest req) {
        ClinicalEncounter e = findOrThrow(id);
        if (!e.isInpatient()) throw new BusinessRuleViolationException("Discharge only applies to inpatient encounters");
        Instant t = req.dischargeAt() != null ? req.dischargeAt() : Instant.now();
        e.recordDischarge(t);
        if (req.dischargeNotes() != null) { if (e.getVitalData()==null) e.setVitalData(new HashMap<>()); e.getVitalData().put("dischargeNotes", req.dischargeNotes()); }
        ClinicalEncounter saved = encounterRepo.save(e);

        // Auto-release bed if patient was in one
        if (saved.isHasBed()) {
            try {
                bedService.releaseBed(saved.getId());
            } catch (Exception ex) {
                log.error("Failed to auto-release bed for encounter {}: {}", saved.getId(), ex.getMessage());
            }
        }

        return encounterMapper.toResponse(saved, resolvePatientName(saved.getPatientId()), resolvePatientNumber(saved.getPatientId()));
    }

    @Transactional
    public void updateConsultantShare(UUID id, String consultantId, Map<String, Object> shareData) {
        ClinicalEncounter e = findOrThrow(id); e.updateConsultantShare(consultantId, shareData); encounterRepo.save(e);
    }

    @Transactional
    public EncounterResponse cancelEncounter(UUID id) {
        ClinicalEncounter e = findOrThrow(id);
        if (e.isInpatient() && e.isHasBed()) throw new BusinessRuleViolationException("Release bed before cancelling");
        e.setCancelled(true);
        ClinicalEncounter saved = encounterRepo.save(e);
        return encounterMapper.toResponse(saved, resolvePatientName(saved.getPatientId()), resolvePatientNumber(saved.getPatientId()));
    }

    private ClinicalEncounter findOrThrow(UUID id) {
        return encounterRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", id));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OP/IP Clinical Tab methods — added for Prescription, Diagnostic Order,
    // Progress Notes, Nurse Notes, Other Charges, IP Vitals History
    // ══════════════════════════════════════════════════════════════════════════

    @Transactional
    public EncounterResponse appendIpVitals(UUID id, RecordVitalsRequest req) {
        ClinicalEncounter e = findOrThrow(id);
        if (e.getVitalData() == null) e.setVitalData(new HashMap<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>)
            e.getVitalData().computeIfAbsent("vitals_history", k -> new ArrayList<>());

        Map<String, Object> entry = new java.util.LinkedHashMap<>(req.vitals());
        entry.put("id",         UUID.randomUUID().toString());
        entry.put("recordedAt", Instant.now().toString());
        history.add(entry);

        if (e.getEncounterStatus() == EncounterStatus.CHECKED_IN) {
            e.updateStatus(EncounterStatus.CONSULTATION_STARTED);
        }
        ClinicalEncounter saved = encounterRepo.save(e);
        return encounterMapper.toResponse(saved, resolvePatientName(saved.getPatientId()), resolvePatientNumber(saved.getPatientId()));
    }

    @Transactional
    public com.hms.api.opip.response.PrescriptionResponse addPrescription(UUID encounterId, com.hms.api.opip.request.AddPrescriptionRequest req) {
        ClinicalEncounter e = findOrThrow(encounterId);
        if (e.getConsultantShareMap() == null) e.setConsultantShareMap(new HashMap<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prescriptions = (List<Map<String, Object>>)
            e.getConsultantShareMap().computeIfAbsent("prescriptions", k -> new ArrayList<>());

        String requestedByName = resolveConsultantName(req.requestedById());
        UUID   prescriptionId  = UUID.randomUUID();
        Instant now            = Instant.now();

        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (com.hms.api.opip.request.AddPrescriptionRequest.PrescriptionLineRequest line : req.items()) {
            Map<String, Object> l = new java.util.LinkedHashMap<>();
            l.put("id", UUID.randomUUID().toString());
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
        Map<String, Object> rx = new java.util.LinkedHashMap<>();
        rx.put("id",              prescriptionId.toString());
        rx.put("encounterId",     encounterId.toString());
        rx.put("requestedById",   req.requestedById() != null ? req.requestedById().toString() : null);
        rx.put("requestedByName", requestedByName);
        rx.put("createdAt",       now.toString());
        rx.put("items",           lineItems);
        prescriptions.add(rx);
        encounterRepo.save(e);

        List<com.hms.api.opip.response.PrescriptionResponse.PrescriptionLineResponse> responseLines = req.items().stream()
            .map(l -> new com.hms.api.opip.response.PrescriptionResponse.PrescriptionLineResponse(
                UUID.randomUUID(), l.drugItemId(), l.drugName(), l.frequency(), l.duration(),
                l.qty(), l.instructionId(), l.instructionLabel(), l.routeId(), l.routeLabel(), l.remarks()
            )).toList();
        return new com.hms.api.opip.response.PrescriptionResponse(prescriptionId, encounterId, req.requestedById(), requestedByName, now, responseLines);
    }

    @Transactional
    public com.hms.api.opip.response.VisitDiagnosticOrderResponse addDiagnosticOrder(UUID encounterId, com.hms.api.opip.request.AddDiagnosticOrderRequest req) {
        ClinicalEncounter e = findOrThrow(encounterId);
        if (e.getConsultantShareMap() == null) e.setConsultantShareMap(new HashMap<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>)
            e.getConsultantShareMap().computeIfAbsent("diagnostic_orders", k -> new ArrayList<>());

        String requestedByName = resolveConsultantName(req.requestedById());
        UUID   orderId         = UUID.randomUUID();
        Instant now            = Instant.now();

        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (com.hms.api.opip.request.AddDiagnosticOrderRequest.DiagnosticOrderLineRequest line : req.items()) {
            Map<String, Object> l = new java.util.LinkedHashMap<>();
            l.put("id",               UUID.randomUUID().toString());
            l.put("diagnosticTestId", line.diagnosticTestId());
            l.put("testName",         line.testName());
            l.put("category",         line.category());
            l.put("status",           "ORDERED");
            lineItems.add(l);
        }
        Map<String, Object> order = new java.util.LinkedHashMap<>();
        order.put("id",              orderId.toString());
        order.put("encounterId",     encounterId.toString());
        order.put("requestedById",   req.requestedById() != null ? req.requestedById().toString() : null);
        order.put("requestedByName", requestedByName);
        order.put("orderedAt",       now.toString());
        order.put("items",           lineItems);
        orders.add(order);
        encounterRepo.save(e);

        List<com.hms.api.opip.response.VisitDiagnosticOrderResponse.DiagnosticOrderLineResponse> responseLines = req.items().stream()
            .map(l -> new com.hms.api.opip.response.VisitDiagnosticOrderResponse.DiagnosticOrderLineResponse(
                UUID.randomUUID(), l.diagnosticTestId(), l.testName(), l.category(), "ORDERED"
            )).toList();

        return new com.hms.api.opip.response.VisitDiagnosticOrderResponse(orderId, encounterId, req.requestedById(), requestedByName, now, responseLines);
    }

    @Transactional
    public com.hms.api.opip.response.ClinicalNoteResponse addClinicalNote(UUID encounterId, String notes, Instant noteAt, UUID requestedById, String noteKey) {
        ClinicalEncounter e = findOrThrow(encounterId);
        if (e.getConsultantShareMap() == null) e.setConsultantShareMap(new HashMap<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>)
            e.getConsultantShareMap().computeIfAbsent(noteKey, k -> new ArrayList<>());

        String requestedByName = resolveConsultantName(requestedById);
        UUID    noteId = UUID.randomUUID();
        Instant now    = Instant.now();
        Instant at     = noteAt != null ? noteAt : now;

        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("id",              noteId.toString());
        entry.put("encounterId",     encounterId.toString());
        entry.put("notes",           notes);
        entry.put("noteAt",          at.toString());
        entry.put("requestedById",   requestedById != null ? requestedById.toString() : null);
        entry.put("requestedByName", requestedByName);
        entry.put("createdAt",       now.toString());
        list.add(entry);
        encounterRepo.save(e);
        return new com.hms.api.opip.response.ClinicalNoteResponse(noteId, encounterId, notes, at, requestedById, requestedByName, now);
    }

    @Transactional
    public com.hms.api.opip.response.OtherChargeResponse addOtherCharge(UUID encounterId, com.hms.api.opip.request.AddOtherChargeRequest req) {
        ClinicalEncounter e = findOrThrow(encounterId);
        if (e.getConsultantShareMap() == null) e.setConsultantShareMap(new HashMap<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> charges = (List<Map<String, Object>>)
            e.getConsultantShareMap().computeIfAbsent("other_charges", k -> new ArrayList<>());

        UUID    chargeId = UUID.randomUUID();
        Instant now      = Instant.now();

        Map<String, Object> entry = new java.util.LinkedHashMap<>();
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
        return new com.hms.api.opip.response.OtherChargeResponse(chargeId, encounterId, req.chargeLabel(), req.serviceCatalogItemId(), req.amount(), req.qty() > 0 ? req.qty() : 1, req.remarks(), now);
    }

    private String resolveConsultantName(UUID consultantId) {
        if (consultantId == null) return null;
        return consultantRepo.findById(consultantId)
            .map(c -> (c.getSalutation() != null ? c.getSalutation() + " " : "")
                       + c.getFirstName() + " " + c.getLastName())
            .orElse(null);
    }

}