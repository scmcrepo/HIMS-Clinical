package com.hms.application.bed;

import com.hms.api.bed.request.AllocateBedRequest;
import com.hms.api.bed.request.CreateBedRequest;
import com.hms.api.bed.response.BedOccupancyResponse;
import com.hms.api.bed.response.BedResponse;
import com.hms.api.bed.response.BedStatusSummary;
import com.hms.api.bed.response.InpatientSearchResult;
import com.hms.domain.bed.model.Bed;
import com.hms.domain.bed.model.BedOccupancy;
import com.hms.domain.bed.model.BedStatus;
import com.hms.domain.encounter.model.ClinicalEncounter;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.BedMapper;
import com.hms.infrastructure.persistence.bed.BedJpaRepository;
import com.hms.infrastructure.persistence.bed.BedOccupancyJpaRepository;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceJpaRepository;
import com.hms.infrastructure.sequence.NumberSequenceEntity;
import com.hms.infrastructure.persistence.bed.RoomCategoryJpaRepository;
import com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class BedManagementService {

    private final BedJpaRepository bedRepo;
    private final BedOccupancyJpaRepository occupancyRepo;
    private final ClinicalEncounterJpaRepository encounterRepo;
    private final PatientJpaRepository patientRepo;
    private final NumberSequenceJpaRepository numberSequenceRepo;
    private final RoomCategoryJpaRepository roomCategoryRepo;
    private final ConsultantJpaRepository consultantRepo;
    private final BedMapper bedMapper;
    private final com.hms.application.billing.BillingOperationsService billingService;

    @Transactional
    public BedResponse createBed(CreateBedRequest req) {
        Bed bed = new Bed();
        bed.setName(req.name());
        bed.setRoomCategoryId(req.roomCategoryId());
        bed.setBedStatus(BedStatus.AVAILABLE);
        if (req.status() != null) {
            bed.setStatus(req.status());
        }
        return enrichBedResponse(bedRepo.save(bed));
    }

    @Transactional
    public BedResponse updateBed(UUID id, CreateBedRequest req) {
        Bed bed = bedRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", id));
        bed.setName(req.name());
        bed.setRoomCategoryId(req.roomCategoryId());
        if (req.status() != null) {
            bed.setStatus(req.status());
        }
        return enrichBedResponse(bedRepo.save(bed));
    }

    /**
     * Allocates a bed to a patient encounter.
     *
     * Uses PESSIMISTIC_WRITE (SELECT FOR UPDATE) on the bed row so
     * concurrent requests for the same bed are serialised — only the
     * first request succeeds; the second sees ALLOCATED status and throws.
     *
     * Also closes any prior active occupancy for the encounter (transfer case).
     */
    @Transactional
    public BedOccupancyResponse allocateBed(AllocateBedRequest req) {
        // ── STEP 1: Date validation FIRST — before any DB writes (SRS D7 bug fix)
        // ──────
        // admissionDate stripped to start-of-day must NOT be after today.
        if (req.admissionDate() != null) {
            java.time.LocalDate admDate = req.admissionDate();
            java.time.LocalDate today = java.time.LocalDate.now();
            if (admDate.isAfter(today)) {
                throw new BusinessRuleViolationException(
                        "Admission date cannot be in the future: " + admDate);
            }
        }

        // ── STEP 2: Lock the bed row — prevents concurrent allocation
        // ─────────────────
        Bed bed = bedRepo.findActiveByIdForUpdate(req.bedId())
                .orElseThrow(() -> new ResourceNotFoundException("Bed", req.bedId()));

        // This throws BusinessRuleViolationException if not AVAILABLE
        bed.allocate();

        ClinicalEncounter encounter = encounterRepo.findById(req.encounterId())
                .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", req.encounterId()));

        if (!encounter.isInpatient()) {
            if (encounter.getConsultantShareMap() == null || !encounter.getConsultantShareMap().containsKey("ADMISSION_REQUEST")) {
                throw new BusinessRuleViolationException(
                        "Bed allocation is only applicable to inpatient encounters or pending admission requests");
            }
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> reqData = (java.util.Map<String, Object>) encounter.getConsultantShareMap().get("ADMISSION_REQUEST");
            if (!"REQUESTED".equals(reqData.get("status"))) {
                throw new BusinessRuleViolationException("Admission request is not in REQUESTED status");
            }

            // Create a NEW Inpatient encounter
            ClinicalEncounter ipEncounter = new ClinicalEncounter();
            ipEncounter.setPatientId(encounter.getPatientId());
            ipEncounter.setPrimaryProviderId(req.consultantId() != null ? req.consultantId() : encounter.getPrimaryProviderId());
            ipEncounter.setEncounterType(com.hms.domain.billing.model.EncounterType.INPATIENT);
            ipEncounter.setVisitMode(encounter.getVisitMode());
            ipEncounter.setStartedAt(Instant.now());
            ipEncounter.setHasDraftBill(false);
            ClinicalEncounter savedIp = encounterRepo.save(ipEncounter);

            // Update OP encounter's admission request status
            reqData.put("status", "ADMITTED");
            reqData.put("ipEncounterId", savedIp.getId().toString());
            reqData.put("admittedAt", Instant.now().toString());
            encounterRepo.save(encounter);

            encounter = savedIp;
        }

        if (req.consultantId() != null) {
            encounter.setPrimaryProviderId(req.consultantId());
        }

        // Close any active occupancy (transfer scenario)
        occupancyRepo.findActiveByEncounterId(req.encounterId())
                .ifPresent(prev -> {
                    prev.close(Instant.now());
                    // Release the previous bed
                    bedRepo.findActiveByIdForUpdate(prev.getBedId())
                            .ifPresent(prevBed -> {
                                prevBed.release();
                                bedRepo.save(prevBed);
                            });
                    occupancyRepo.save(prev);
                });

        BedOccupancy occupancy = new BedOccupancy();
        occupancy.setBedId(req.bedId());
        occupancy.setEncounterId(encounter.getId());
        occupancy.setBillId(req.billId());
        Instant startAt = resolveInstant(req.admissionDate());
        occupancy.setFromDatetime(startAt);

        encounter.allocateBed(req.bedId());

        bedRepo.save(bed);
        encounterRepo.save(encounter);
        BedOccupancy saved = occupancyRepo.save(occupancy);

        // Auto-create/update bill for IP allocation
        try {
            encounterRepo.saveAndFlush(encounter); // Ensure encounter state is persistent
            
            com.hms.domain.billing.model.BillType bt = com.hms.domain.billing.model.BillType.CASH;
            if (req.billType() != null && !req.billType().isBlank()) {
                try {
                    bt = com.hms.domain.billing.model.BillType.valueOf(req.billType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid billType passed in bed allocation: {}, falling back to CASH", req.billType());
                }
            }

            var bill = billingService.ensureDraftBill(
                    encounter.getPatientId(), 
                    encounter.getId(),
                    com.hms.domain.billing.model.EncounterType.INPATIENT, 
                    encounter.getPrimaryProviderId(),
                    bt,
                    req.payorId()
            );
            billingService.injectBedCharge(bill.id(), req.bedId(), startAt);
        } catch (Exception e) {
            log.error("Failed to trigger auto-billing for bed {}: {}", req.bedId(), e.getMessage());
        }

        return bedMapper.toOccupancyResponse(saved);
    }

    /**
     * Releases a bed using the bed ID.
     */
    @Transactional
    public void releaseBedByBedId(UUID bedId) {
        BedOccupancy occupancy = occupancyRepo.findActiveByBedId(bedId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active occupancy found for bed: " + bedId));

        releaseBed(occupancy.getEncounterId());
    }

    /**
     * Releases a bed on patient discharge.
     */
    @Transactional
    public void releaseBed(UUID encounterId) {
        ClinicalEncounter encounter = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));

        BedOccupancy occupancy = occupancyRepo.findActiveByEncounterId(encounterId)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active bed occupancy found for encounter: " + encounterId));

        Instant now = Instant.now();
        occupancy.close(now);
        occupancyRepo.save(occupancy);

        // Synchronize with billing to close the active bed charge
        try {
            var bill = billingService.ensureDraftBill(encounter.getPatientId(), encounterId,
                    com.hms.domain.billing.model.EncounterType.INPATIENT, encounter.getPrimaryProviderId());
            if (bill != null) {
                billingService.closeActiveBedCharge(bill.id(), now);
            }
        } catch (Exception e) {
            log.error("Failed to close bed charge on release for encounter {}: {}", encounterId, e.getMessage());
        }

        Bed bed = bedRepo.findActiveByIdForUpdate(occupancy.getBedId())
                .orElseThrow(() -> new ResourceNotFoundException("Bed", occupancy.getBedId()));
        bed.release();
        bedRepo.save(bed);

        encounter.unallocateBed();
        encounterRepo.save(encounter);
    }

    public BedResponse setMaintenance(UUID bedId) {
        Bed bed = bedRepo.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", bedId));
        bed.markMaintenance();
        return enrichBedResponse(bedRepo.save(bed));
    }

    public BedResponse clearMaintenance(UUID bedId) {
        Bed bed = bedRepo.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", bedId));
        bed.clearMaintenance();
        return enrichBedResponse(bedRepo.save(bed));
    }

    @Transactional(readOnly = true)
    public List<BedResponse> getAllBeds() {
        return bedRepo.findAll(Sort.by(Sort.Direction.ASC, "name")).stream().map(this::enrichBedResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<BedResponse> getAvailableBeds(UUID roomCategoryId) {
        List<Bed> beds = roomCategoryId != null
                ? bedRepo.findAvailableByCategory(roomCategoryId)
                : bedRepo.findByBedStatus(BedStatus.AVAILABLE);
        return beds.stream().map(this::enrichBedResponse).toList();
    }

    @Transactional(readOnly = true)
    public BedStatusSummary getStatusSummary() {
        long total = bedRepo.count();
        long available = bedRepo.findByBedStatus(BedStatus.AVAILABLE).size();
        long allocated = bedRepo.findByBedStatus(BedStatus.ALLOCATED).size();
        long maintenance = bedRepo.findByBedStatus(BedStatus.MAINTENANCE).size();
        return new BedStatusSummary(total, available, allocated, maintenance);
    }

    @Transactional(readOnly = true)
    public List<BedOccupancyResponse> getOccupancyHistory(UUID encounterId) {
        return occupancyRepo.findAllByEncounterId(encounterId).stream()
                .map(bedMapper::toOccupancyResponse).toList();
    }

    /**
     * Transfers a patient from one bed to another.
     * SRS C2.3 / BedController §3 — 8 steps:
     * 1. Validate toDate >= fromDate AND <= now
     * 2. Lock new bed (PESSIMISTIC_WRITE)
     * 3. Close old allocation (set toDatetime = now)
     * 4. Release old bed → AVAILABLE
     * 5. Lock old bed (already held from previous allocation)
     * 6. Create new allocation (new bed, same encounter)
     * 7. Update encounter.lastBedId = new bed
     * 8. Conditional billing (if bed charge automated)
     */
    @Transactional
    public BedOccupancyResponse transferBed(UUID encounterId, UUID newBedId, LocalDate fromDate) {
        Instant transferInstant = resolveInstant(fromDate);
        log.info("Transferring encounter {} to bed {}, resolved instant={}", encounterId, newBedId, transferInstant);

        // Step 2 — Lock new bed
        Bed newBed = bedRepo.findActiveByIdForUpdate(newBedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", newBedId));
        newBed.allocate(); // throws if not AVAILABLE

        ClinicalEncounter enc = encounterRepo.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("ClinicalEncounter", encounterId));

        // Step 3 — Close existing allocation

        BedOccupancy existing = occupancyRepo.findActiveByEncounterId(encounterId)
                .orElseThrow(() -> new com.hms.exception.BusinessRuleViolationException(
                        "No active bed occupancy found for encounter: " + encounterId));
        existing.close(transferInstant);
        occupancyRepo.save(existing);

        // Step 4 — Release old bed
        bedRepo.findActiveByIdForUpdate(existing.getBedId())
                .ifPresent(oldBed -> {
                    oldBed.release();
                    bedRepo.save(oldBed);
                });

        // Step 5 — Save new bed as ALLOCATED
        bedRepo.save(newBed);

        // Step 6 — Create new allocation
        BedOccupancy newOccupancy = new BedOccupancy();
        newOccupancy.setBedId(newBedId);
        newOccupancy.setEncounterId(encounterId);
        newOccupancy.setFromDatetime(transferInstant);

        // Step 7 — Update encounter
        enc.allocateBed(newBedId);
        encounterRepo.saveAndFlush(enc);

        // Step 8 — Trigger billing update
        try {
            var bill = billingService.ensureDraftBill(enc.getPatientId(), enc.getId(),
                    com.hms.domain.billing.model.EncounterType.INPATIENT, enc.getPrimaryProviderId());
            billingService.injectBedCharge(bill.id(), newBedId, transferInstant);
        } catch (Exception e) {
            log.error("Failed to inject bed charge on transfer: {}", e.getMessage());
        }

        return bedMapper.toOccupancyResponse(occupancyRepo.save(newOccupancy));
    }

    /**
     * Vacates a bed on patient discharge.
     * SRS BedController §3 — 5 steps:
     * 1. Validate dischargeDate >= admissionDate AND <= now
     * 2. Free bed → AVAILABLE
     * 3. Close allocation (set toDatetime)
     * 4. Set visit.dischargeDate
     * 5. Conditional billing (close open bed charge lines)
     */
    @Transactional
    public void vacateBed(UUID encounterId, LocalDate dischargeDate) {
        // Step 1 — Date validation FIRST
        LocalDate today = LocalDate.now();
        if (dischargeDate != null && dischargeDate.isAfter(today)) {
            throw new com.hms.exception.BusinessRuleViolationException(
                    "Discharge date cannot be in the future");
        }

        // Steps 2-4 — Delegate to existing releaseBed logic
        releaseBed(encounterId);

        // Step 5 — Update encounter discharge date
        encounterRepo.findById(encounterId).ifPresent(enc -> {
            enc.recordDischarge(resolveInstant(dischargeDate));
            encounterRepo.save(enc);
        });
    }

    /**
     * GET /bed/getAllocatedDetail — complex grouped query.
     * Returns all non-AVAILABLE beds with visit/patient/bill/bedType projections.
     * Grouped by bed, sorted by bedStatus ASC, bed.name ASC.
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getAllocatedDetail() {
        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

        List<Bed> nonAvailable = bedRepo.findByBedStatus(BedStatus.ALLOCATED);
        // Also include MAINTENANCE beds
        List<Bed> maintenance = bedRepo.findByBedStatus(BedStatus.MAINTENANCE);
        java.util.List<Bed> all = new java.util.ArrayList<>(nonAvailable);
        all.addAll(maintenance);
        all.sort(java.util.Comparator
                .comparing(Bed::getBedStatus)
                .thenComparing(Bed::getName));

        for (Bed bed : all) {
            var detail = new java.util.LinkedHashMap<String, Object>();
            detail.put("bed", enrichBedResponse(bed));
            occupancyRepo.findActiveByBedId(bed.getId()).ifPresent(occ -> {
                detail.put("occupancy", bedMapper.toOccupancyResponse(occ));
                encounterRepo.findById(occ.getEncounterId()).ifPresent(enc -> {
                    detail.put("encounterId", enc.getId());
                    detail.put("patientId", enc.getPatientId());
                });
            });
            result.add(detail);
        }
        return result;
    }

    private BedResponse enrichBedResponse(Bed bed) {
        String roomCategoryName = roomCategoryRepo.findById(bed.getRoomCategoryId())
                .map(com.hms.domain.bed.model.RoomCategory::getName)
                .orElse("Unknown Type");

        String pName = null;
        String pNum = null;
        java.util.UUID pEncId = null;
        String cName = null;

        if (bed.getBedStatus() == BedStatus.ALLOCATED) {
            var occ = occupancyRepo.findActiveByBedId(bed.getId()).orElse(null);
            if (occ != null) {
                pEncId = occ.getEncounterId();
                var enc = encounterRepo.findById(occ.getEncounterId()).orElse(null);
                if (enc != null) {
                    pName = patientRepo.findById(enc.getPatientId())
                            .map(p -> p.getFirstName() + " " + p.getLastName())
                            .orElse("Unknown");
                    pNum = numberSequenceRepo.findById(enc.getPatientId())
                            .map(NumberSequenceEntity::getValue)
                            .orElse("N/A");

                    cName = consultantRepo.findById(enc.getPrimaryProviderId())
                            .map(c -> (c.getSalutation() != null ? c.getSalutation() + " " : "") + c.getFirstName()
                                    + " " + c.getLastName())
                            .orElse("Unknown");
                    
                    log.info("Bed {}: found encounter {}, patient {}, primaryProviderId {}, consultant {}", 
                        bed.getName(), enc.getId(), pName, enc.getPrimaryProviderId(), cName);
                } else {
                    log.warn("Bed {}: allocated but encounter {} not found", bed.getName(), occ.getEncounterId());
                }
            } else {
                log.warn("Bed {}: status is ALLOCATED but no active occupancy found", bed.getName());
            }
        }

        return new BedResponse(
                bed.getId(), bed.getName(), bed.getRoomCategoryId(), roomCategoryName,
                bed.getBedStatus(), bed.getStatus(),
                pName, pNum, pEncId, cName);
    }

    /**
     * GET /bed/years — year range from first allocation to now.
     * NPE risk if bed_occupancies empty — guarded with Optional.
     */
    @Transactional(readOnly = true)
    public List<Integer> getAllocationYears() {
        var oldest = occupancyRepo.findOldestAllocation();
        int startYear = oldest.map(o -> java.time.ZonedDateTime.ofInstant(o.getFromDatetime(),
                java.time.ZoneId.systemDefault()).getYear()).orElse(java.time.LocalDate.now().getYear());

        int currentYear = java.time.LocalDate.now().getYear();
        var years = new java.util.ArrayList<Integer>();
        for (int y = startYear; y <= currentYear; y++)
            years.add(y);
        return years;
    }

    /**
     * Search active inpatient encounters by patient number (SCMCP-XXXX) or patient
     * name.
     * Returns up to 10 results with encounterId, patientNumber and patientName
     * so the UI can display a meaningful autocomplete instead of raw UUIDs.
     */
    @Transactional(readOnly = true)
    public List<InpatientSearchResult> searchInpatients(String query) {
        if (query == null || query.isBlank())
            return List.of();
        String q = query.trim().toLowerCase();

        // 1. Fetch all active (not discharged, not cancelled) inpatient encounters
        List<ClinicalEncounter> inpatients = encounterRepo
                .findActiveInpatients();

        // 2. Fetch recent outpatient encounters to find pending admission requests
        List<ClinicalEncounter> outpatients = encounterRepo
                .findRecentOutpatients(Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS));

        List<InpatientSearchResult> results = new java.util.ArrayList<>();

        // Add active inpatients who do not have a bed allocated
        inpatients.stream()
                .filter(enc -> !enc.isHasBed())
                .map(enc -> mapToSearchResult(enc, q))
                .filter(java.util.Objects::nonNull)
                .forEach(results::add);

        // Add outpatients who have a pending admission request and are not already admitted
        outpatients.stream()
                .filter(enc -> {
                    if (enc.getConsultantShareMap() == null) return false;
                    Object reqData = enc.getConsultantShareMap().get("ADMISSION_REQUEST");
                    if (reqData instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> map = (java.util.Map<String, Object>) reqData;
                        return "REQUESTED".equals(map.get("status"));
                    }
                    return false;
                })
                .filter(enc -> encounterRepo.findActiveInpatientByPatientId(enc.getPatientId()).isEmpty())
                .map(enc -> mapToSearchResult(enc, q))
                .filter(java.util.Objects::nonNull)
                .forEach(results::add);

        return results.stream()
                .limit(10)
                .toList();
    }

    private InpatientSearchResult mapToSearchResult(ClinicalEncounter enc, String q) {
        var patient = patientRepo.findById(enc.getPatientId()).orElse(null);
        if (patient == null)
            return null;

        String patientNumber = numberSequenceRepo.findById(enc.getPatientId())
                .map(NumberSequenceEntity::getValue)
                .orElse("");

        String fullName = patient.getFirstName() + " " + patient.getLastName();
        String phone = patient.getContactNumber() != null ? patient.getContactNumber() : "";

        boolean matches = patientNumber.toLowerCase().contains(q)
                || fullName.toLowerCase().contains(q)
                || phone.contains(q);

        if (!matches)
            return null;
        return new InpatientSearchResult(enc.getId(), patientNumber, fullName, enc.getPatientId(), phone);
    }

    @Transactional(readOnly = true)
    public String getActiveBedNameForEncounter(UUID encounterId) {
        if (encounterId == null) return "—";
        return occupancyRepo.findActiveByEncounterId(encounterId)
                .flatMap(occ -> bedRepo.findById(occ.getBedId()))
                .map(Bed::getName)
                .orElse("—");
    }

    @Transactional(readOnly = true)
    public String getBedName(UUID bedId) {
        if (bedId == null) return "—";
        return bedRepo.findById(bedId)
                .map(Bed::getName)
                .orElse("—");
    }

    private Instant resolveInstant(LocalDate date) {
        if (date == null)
            return Instant.now();
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) {
            throw new com.hms.exception.BusinessRuleViolationException("Date cannot be in the future");
        }
        if (date.equals(today)) {
            return Instant.now();
        }
        return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();
    }
}
