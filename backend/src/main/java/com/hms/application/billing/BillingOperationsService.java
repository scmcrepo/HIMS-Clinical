package com.hms.application.billing;

import com.hms.api.billing.request.*;
import com.hms.api.billing.response.*;
import com.hms.domain.billing.model.*;
import com.hms.domain.billing.service.*;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.BillMapper;
import com.hms.infrastructure.persistence.billing.BillJpaRepository;
import com.hms.infrastructure.settings.SettingsRegistryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationContext;
import java.time.Instant;
import java.util.*;
import com.hms.domain.diagnostic.model.DiagnosticOrder;
import com.hms.domain.catalog.model.ServiceCatalogItem;
import com.hms.domain.catalog.model.PricingTier;
import com.hms.domain.catalog.model.ServiceCategory;
import com.hms.domain.catalog.model.ServiceCategoryType;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingOperationsService {

    private final BillingEngineFactory engineFactory;
    private final com.hms.infrastructure.persistence.billing.BillDetailModifiedJpaRepository billDetailModifiedRepo;
    private final BillJpaRepository billRepo;
    private final BillMapper billMapper;
    private final SettingsRegistryImpl settingsRegistry;
    private final com.hms.infrastructure.persistence.patient.PatientJpaRepository patientRepo;
    private final com.hms.infrastructure.persistence.diagnostic.DiagnosticOrderJpaRepository diagnosticOrderRepo;
    private final com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository serviceCatalogRepo;
    private final com.hms.infrastructure.persistence.charge.ChargeJpaRepository chargeRepo;
    private final com.hms.infrastructure.sequence.NumberSequenceJpaRepository numberSequenceRepo;
    private final com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository encounterRepo;
    private final com.hms.infrastructure.persistence.bed.BedJpaRepository bedRepo;
    private final com.hms.infrastructure.persistence.bed.RoomCategoryJpaRepository roomCategoryRepo;
    private final com.hms.infrastructure.persistence.consultant.ConsultantJpaRepository consultantRepo;
    private final com.hms.domain.shared.port.out.SequenceNumberPort sequencePort;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<BillSummaryResponse> getAllBills() {
        return billRepo.findAll().stream().map(this::mapWithPatientName).toList();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<BillSummaryResponse> searchBills(
            String query, java.time.LocalDate from, java.time.LocalDate to,
            org.springframework.data.domain.Pageable pageable) {
        List<UUID> pids = null;
        if (query != null && !query.isBlank()) {
            pids = new ArrayList<>();
            pids.addAll(patientRepo.searchIdsByNameOrContact(query));
            pids.addAll(numberSequenceRepo.findIdsByValue(query));
            if (pids.isEmpty())
                return org.springframework.data.domain.Page.empty(pageable);
        }

        return billRepo.searchBills(from, to, pids, pageable)
                .map(this::mapWithPatientName);
    }

    private BillSummaryResponse mapWithPatientName(Bill b) {
        try {
            // Recalculate virtual totals for draft bills so the list view matches details
            hydrateDraftBill(b);

            var patient = patientRepo.findById(b.getPatientId());
            String name = patient.map(p -> p.getFirstName() + " " + p.getLastName()).orElse("Unknown Patient");
            String number = numberSequenceRepo.findById(b.getPatientId())
                    .map(com.hms.infrastructure.sequence.NumberSequenceEntity::getValue)
                    .orElse("-");
            return billMapper.toSummaryResponse(b, name, number);
        } catch (Exception e) {
            log.error("Error mapping bill {} for patient {}", b.getId(), b.getPatientId(), e);
            throw e;
        }
    }

    private BillResponse mapWithPatientInfo(Bill b) {
        String patientName = null;
        String patientNumber = null;
        String patientGender = null;
        String consultantName = null;

        if (b.getPatientId() != null) {
            var patient = patientRepo.findById(b.getPatientId());
            patientName = patient.map(p -> p.getFirstName() + " " + p.getLastName()).orElse(null);
            patientGender = patient.map(p -> p.getGender() != null ? p.getGender().name() : null).orElse(null);
            patientNumber = numberSequenceRepo.findById(b.getPatientId())
                    .map(com.hms.infrastructure.sequence.NumberSequenceEntity::getValue).orElse(null);
        }

        if (b.getPrimaryProviderId() != null) {
            consultantName = consultantRepo.findById(b.getPrimaryProviderId())
                    .map(c -> (c.getSalutation() != null ? c.getSalutation() + " " : "") + c.getFirstName() + " "
                            + c.getLastName())
                    .orElse(null);
        } else if (b.getEncounterId() != null) {
            var encounter = encounterRepo.findById(b.getEncounterId()).orElse(null);
            if (encounter != null && encounter.getPrimaryProviderId() != null) {
                consultantName = consultantRepo.findById(encounter.getPrimaryProviderId())
                        .map(c -> (c.getSalutation() != null ? c.getSalutation() + " " : "") + c.getFirstName() + " "
                                + c.getLastName())
                        .orElse(null);
            }
        }

        return billMapper.toResponse(b, patientName, patientNumber, patientGender, consultantName);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Transactional
    public BillResponse createBill(CreateBillRequest req) {
        // If outpatient, check for existing draft bill to avoid duplicates and "old
        // tests" issues
        if (req.encounterType() == EncounterType.OUTPATIENT) {
            List<Bill> existing = billRepo.findDraftBillsByPatientId(req.patientId());
            for (Bill b : existing) {
                // Resume existing draft if it matches type/encounter
                if (b.getBillType() == req.billType() && Objects.equals(b.getEncounterId(), req.encounterId())) {
                    log.info("Resuming existing draft bill {} for patient {}", b.getId(), req.patientId());
                    return getBillById(b.getId());
                }
            }
        } else if (req.encounterType() == EncounterType.INPATIENT) {
            List<Bill> existing = billRepo.findDraftBillsByPatientId(req.patientId());
            for (Bill b : existing) {
                if (b.getEncounterType() == EncounterType.INPATIENT) {
                    log.info("Found existing draft IP bill {} for patient {}, resuming.", b.getId(), req.patientId());
                    return getBillById(b.getId());
                }
            }
        }

        UUID providerId = req.primaryProviderId();
        if (providerId == null && req.encounterId() != null) {
            providerId = encounterRepo.findById(req.encounterId())
                    .map(com.hms.domain.encounter.model.ClinicalEncounter::getPrimaryProviderId)
                    .orElse(null);
        }

        BillingEngine engine = engineFactory.createDraft(req.patientId(), req.billType(), req.encounterType(),
                providerId);
        if (req.encounterId() != null)
            engine.getBill().setEncounterId(req.encounterId());
        if (req.payorId() != null)
            engine.getBill().setPayorId(req.payorId());
        if (req.referralId() != null)
            engine.getBill().setReferralId(req.referralId());
        if (req.admissionAt() != null)
            engine.getBill().setAdmissionAt(req.admissionAt());

        Bill saved = billRepo.saveAndFlush(engine.getBill());
        log.info("Created new draft bill {} for patient {}", saved.getId(), req.patientId());

        // Auto-inject bed charge for IP bills if active encounter has a bed
        if (saved.isInpatient() && saved.getEncounterId() != null) {
            try {
                encounterRepo.findById(saved.getEncounterId()).ifPresent(enc -> {
                    if (enc.isHasBed() && enc.getLastBedId() != null) {
                        injectBedCharge(saved.getId(), enc.getLastBedId(), enc.getStartedAt());
                        bedRepo.findById(enc.getLastBedId()).ifPresent(bed -> {
                            saved.setBedNumber(bed.getName());
                            billRepo.saveAndFlush(saved);
                        });
                    }
                });
            } catch (Exception e) {
                log.error("Failed to auto-inject initial bed charge or set bed number: {}", e.getMessage());
            }
        }

        return mapWithPatientInfo(saved);
    }

    @Transactional
    public void injectBedCharge(UUID billId, UUID bedId, Instant fromAt) {
        log.info("Injecting bed charge for bill {} and bed {} from {}", billId, bedId, fromAt);
        Bill bill = billRepo.findById(billId).orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
        com.hms.domain.bed.model.Bed bed = bedRepo.findById(bedId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", bedId));

        var category = roomCategoryRepo.findById(bed.getRoomCategoryId());
        String catName = category.map(com.hms.domain.bed.model.RoomCategory::getName).orElse("");
        StringBuilder sb = new StringBuilder(bed.getName());
        List<String> details = new ArrayList<>();
        if (!catName.isEmpty())
            details.add(catName);

        if (!details.isEmpty()) {
            sb.append(" (").append(String.join(" | ", details)).append(")");
        }
        String targetItemName = sb.toString();

        log.info("Target bed name: '{}' (Bill ID: {})", targetItemName, billId);

        // Check if this bed is already open on the bill
        boolean alreadyOpen = bill.getChargeLineItems().stream()
                .anyMatch(item -> targetItemName.equals(item.getItemName()) && item.getBedChargeFrom() != null
                        && item.getBedChargeTo() == null);

        if (alreadyOpen) {
            log.info("Bed {} is already open on bill {}, skipping injection", targetItemName, billId);
            return;
        }

        // Close any other open bed charges for this bill
        closeActiveBedCharge(billId, fromAt);

        ChargeLineItem bedCharge = new ChargeLineItem();
        bedCharge.setBill(bill);
        bedCharge.setItemName(targetItemName);
        bedCharge.setQuantity(0); // Mark as running charge
        bedCharge.setAmount(0);
        bedCharge.setBedChargeFrom(fromAt != null ? fromAt : Instant.now());

        // Fetch service catalog item ID from room category
        category.ifPresent(cat -> {
            bedCharge.setServiceCatalogItemId(cat.getServiceCatalogItemId());
        });

        // Fallback to the 'Hospital Bed' item created in V024
        if (bedCharge.getServiceCatalogItemId() == null) {
            bedCharge.setServiceCatalogItemId(UUID.fromString("748c116d-33d3-4fc6-879e-4c22762b0001"));
        }

        // Dynamically resolve rate based on bill and serviceCatalogItemId
        bedCharge.setUnitRate(resolveRate(bill, bedCharge.getServiceCatalogItemId()));

        log.info("Adding new bed charge line for {} starting at {}", targetItemName, fromAt);
        bill.getChargeLineItems().add(bedCharge);
        billRepo.saveAndFlush(bill);
        log.info("Bed charge saved successfully for bill {}", billId);
    }

    @Transactional
    public void closeActiveBedCharge(UUID billId, Instant toAt) {
        Bill bill = billRepo.findById(billId).orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
        Instant closeTime = (toAt != null) ? toAt : Instant.now();

        bill.getChargeLineItems().stream()
                .filter(item -> item.getBedChargeFrom() != null && item.getBedChargeTo() == null)
                .forEach(item -> {
                    log.info("Closing active bed charge: {} on bill {} at {}", item.getItemName(), billId, closeTime);
                    item.setBedChargeTo(closeTime);

                    // For draft bills, also compute the final quantity/amount immediately
                    if (bill.isDraft() && settingsRegistry.isBedChargeAutomated()) {
                        BedChargeCalculator.computeBedCharges(List.of(item), closeTime, true);
                    }
                });
        billRepo.saveAndFlush(bill);
    }

    @Transactional
    public void injectBedChargeByEncounter(UUID encounterId, UUID bedId, Instant fromAt) {
        log.info("Injecting bed charge by encounter: {}, bed: {}", encounterId, bedId);
        encounterRepo.findById(encounterId).ifPresent(enc -> {
            var bill = ensureDraftBill(enc.getPatientId(), enc.getId(), enc.getEncounterType(),
                    enc.getPrimaryProviderId());
            if (bill != null) {
                log.info("Found/Created bill {} for encounter {}", bill.id(), encounterId);
                injectBedCharge(bill.id(), bedId, fromAt);
            } else {
                log.warn("Could not find or create draft bill for encounter {}", encounterId);
            }
        });
    }

    @Transactional
    public BillResponse recordPayment(UUID billId, RecordPaymentRequest req) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        hydrateDraftBill(bill);
        BillingEngine engine = new BillingEngine(bill, sequencePort, eventPublisher);

        req.payments().forEach(entry -> {
            Payment p = new Payment();
            p.setAmount(entry.amount());
            p.setPaymentMode(entry.paymentMode());
            p.setPaymentType(entry.paymentType());
            p.setNotes(entry.notes());
            p.setRecordedAt(Instant.now());
            p.setSequenceNumber(sequencePort.generateNext(switch (entry.paymentType()) {
                case DEPOSIT -> DocumentType.DEPOSIT;
                case REFUND -> DocumentType.REFUND;
                case ADVANCE_REFUND -> DocumentType.ADVANCE_REFUND;
                default -> DocumentType.RECEIPT;
            }));
            engine.recordPayment(p);
        });

        Bill saved = billRepo.save(engine.getBill());

        // Step 1: Auto-create diagnostic orders from charges (needed before marking)
        // - For non-draft bills (SETTLED / WITH_DUE) → handled by
        // autoCreateDiagnosticsIfSettled
        // - For OP DRAFT bills with a deposit → also create immediately so they appear
        // in diagnostics
        boolean opDraftWithDeposit = saved.isDraft() && saved.isOutpatient() && saved.getPaymentTotal() > 0;
        if (opDraftWithDeposit) {
            autoCreateDiagnosticsFromCharges(saved);
        } else {
            autoCreateDiagnosticsIfSettled(saved);
        }

        // Step 2: Mark associated diagnostic orders based on payment state
        boolean shouldMark = !saved.isDraft() || opDraftWithDeposit;
        if (shouldMark) {
            try {
                var diagnosticService = applicationContext
                        .getBean(com.hms.application.diagnostic.DiagnosticOrderingService.class);
                var linkedOrderIds = saved.getChargeLineItems().stream()
                        .filter(item -> item.getDiagnosticOrderLineId() != null && item.isActive())
                        .map(ChargeLineItem::getDiagnosticOrderId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();

                if (saved.getBillStatus() == com.hms.domain.billing.model.BillStatus.SETTLED) {
                    // Fully paid — mark all linked orders as BILLED
                    linkedOrderIds.forEach(diagnosticService::markBilled);
                } else {
                    // Partially paid (WITH_DUE or DRAFT with deposit) — mark as PART_PAID
                    linkedOrderIds.forEach(diagnosticService::markPartPaid);
                }
            } catch (Exception e) {
                log.error("Error updating diagnostic order status for bill {}", saved.getId(), e);
            }
        }
        return mapWithPatientInfo(saved);
    }

    private void autoCreateDiagnosticsIfSettled(Bill saved) {
        // Auto-create diagnostic orders for:
        // - SETTLED: fully paid OP bill
        // - WITH_DUE: partially paid OP bill (PART_PAID status)
        // - IP DRAFT: immediately on charge add (IP flow)
        if (saved.getBillStatus() == com.hms.domain.billing.model.BillStatus.SETTLED
                || saved.getBillStatus() == com.hms.domain.billing.model.BillStatus.WITH_DUE
                || (saved.isInpatient() && saved.isDraft())) {
            autoCreateDiagnosticsFromCharges(saved);
        }
    }

    private void autoCreateDiagnosticsFromCharges(Bill saved) {
        try {
            var diagnosticService = applicationContext
                    .getBean(com.hms.application.diagnostic.DiagnosticOrderingService.class);
            var categoryService = applicationContext
                    .getBean(com.hms.infrastructure.persistence.category.CategoryJpaRepository.class);

            java.util.Map<com.hms.domain.diagnostic.model.DiagnosticType, java.util.List<com.hms.domain.billing.model.ChargeLineItem>> groupedLines = new java.util.HashMap<>();

            for (com.hms.domain.billing.model.ChargeLineItem item : saved.getChargeLineItems()) {
                if (item.getServiceCatalogItemId() != null && item.isActive()
                        && item.getDiagnosticOrderLineId() == null) {
                    var charge = chargeRepo.findById(item.getServiceCatalogItemId());
                    if (charge.isPresent() && charge.get().getCategoryId() != null) {
                        var category = categoryService.findById(charge.get().getCategoryId());
                        if (category.isPresent() && category.get()
                                .getChargeCategoryType() == com.hms.domain.shared.model.ChargeCategoryType.DIAGNOSTICS) {
                            String catName = category.get().getName().toUpperCase();
                            String itemName = item.getItemName() != null ? item.getItemName().toUpperCase() : "";

                            String combined = catName + " " + itemName;
                            
                            // Prevent beds/rooms from being incorrectly categorized as diagnostics
                            if (combined.contains("ROOM") || combined.contains("BED") || combined.contains("WARD")) {
                                continue;
                            }

                            boolean isRad = combined.contains("RADIOLOGY") || combined.contains("IMAGING") ||
                                    combined.contains("X-RAY") || combined.contains("SCAN") ||
                                    combined.contains("MRI") || combined.contains("CT ") ||
                                    combined.contains("ULTRASOUND") || combined.contains("USG") ||
                                    combined.contains("SONOGRAPHY") || combined.contains("XRAY");

                            var type = isRad ? com.hms.domain.diagnostic.model.DiagnosticType.RADIOLOGY
                                    : com.hms.domain.diagnostic.model.DiagnosticType.LAB;

                            groupedLines.computeIfAbsent(type, k -> new java.util.ArrayList<>()).add(item);
                        }
                    }
                }
            }

            for (var entry : groupedLines.entrySet()) {
                var type = entry.getKey();
                var items = entry.getValue();

                var lineReqs = items.stream()
                        .map(item -> new com.hms.api.diagnostic.request.PlaceOrderRequest.OrderLineRequest(
                                item.getServiceCatalogItemId(), item.getItemName(), null, "Auto-created from bill"))
                        .toList();

                var req = new com.hms.api.diagnostic.request.PlaceOrderRequest(
                        saved.getEncounterId(), saved.getPatientId(), saved.getPrimaryProviderId(),
                        type, saved.getId(), lineReqs);

                var savedOrder = diagnosticService.placeOrder(req);

                java.util.Set<UUID> alreadyLinkedLineIds = saved.getChargeLineItems().stream()
                        .map(com.hms.domain.billing.model.ChargeLineItem::getDiagnosticOrderLineId)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.toSet());

                for (var item : items) {
                    savedOrder.lines().stream()
                            .filter(l -> l.serviceCatalogItemId() != null && l.serviceCatalogItemId().equals(item.getServiceCatalogItemId()))
                            .filter(l -> !alreadyLinkedLineIds.contains(l.id()))
                            .findFirst()
                            .ifPresent(lineResp -> {
                                alreadyLinkedLineIds.add(lineResp.id());
                                item.setDiagnosticOrderId(savedOrder.id());
                                item.setDiagnosticOrderLineId(lineResp.id());
                            });
                }
            }
            if (!groupedLines.isEmpty()) {
                billRepo.saveAndFlush(saved);
            }
        } catch (Exception e) {
            log.error("Failed to auto-create diagnostics from charges: {}", e.getMessage());
        }
    }

    @Transactional
    public BillResponse generateBill(UUID billId, GenerateBillRequest req) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        hydrateDraftBill(bill);
        BillingEngine engine = new BillingEngine(bill, sequencePort, eventPublisher);

        engine.generateBill(req.billDate(), req.dischargeAt());

        if (engine.isBillGenerated()) {
            engine.getBill().setBillNumber(sequencePort.generateNext(DocumentType.BILL));
        }

        Bill saved = billRepo.save(engine.getBill());

        // Auto-create diagnostic orders from charge lines (for OP: SETTLED or WITH_DUE)
        autoCreateDiagnosticsIfSettled(saved);

        // Mark diagnostic orders based on final bill status
        try {
            var diagnosticService = applicationContext
                    .getBean(com.hms.application.diagnostic.DiagnosticOrderingService.class);
            var linkedOrderIds = saved.getChargeLineItems().stream()
                    .filter(item -> item.getDiagnosticOrderLineId() != null && item.isActive())
                    .map(ChargeLineItem::getDiagnosticOrderId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();

            if (saved.getBillStatus() == com.hms.domain.billing.model.BillStatus.SETTLED) {
                // Fully paid — mark as BILLED
                linkedOrderIds.forEach(diagnosticService::markBilled);
            } else if (saved.getBillStatus() == com.hms.domain.billing.model.BillStatus.WITH_DUE) {
                // Partially paid — mark as PART_PAID so they appear in diagnostics
                linkedOrderIds.forEach(diagnosticService::markPartPaid);
            }
        } catch (Exception e) {
            log.error("Error marking diagnostic orders for bill {}", saved.getId(), e);
        }
        return mapWithPatientInfo(saved);
    }

    @Transactional
    public BillResponse applyDiscount(UUID billId, ApplyDiscountRequest req) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        hydrateDraftBill(bill);
        BillingEngine engine = new BillingEngine(bill, sequencePort, eventPublisher);

        List<BillingEngine.LineItemDiscount> lineDiscounts = req.lineDiscounts().stream()
                .map(d -> new BillingEngine.LineItemDiscount(d.chargeLineItemId(), d.amount())).toList();

        engine.applyDiscount(req.totalDiscount(), lineDiscounts);
        return mapWithPatientInfo(billRepo.save(engine.getBill()));
    }

    @Transactional
    public BillResponse cancelDiscount(UUID billId) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        hydrateDraftBill(bill);
        BillingEngine engine = new BillingEngine(bill, sequencePort, eventPublisher);
        engine.cancelDiscount();
        return mapWithPatientInfo(billRepo.save(engine.getBill()));
    }

    @Transactional
    public BillResponse addChargeLineItem(UUID billId, AddChargeRequest req) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        hydrateDraftBill(bill);
        BillingEngine engine = new BillingEngine(bill, sequencePort, eventPublisher);

        ChargeLineItem item = new ChargeLineItem();
        item.setServiceCatalogItemId(req.serviceCatalogItemId());
        item.setUnitRate(req.unitRate());
        item.setQuantity(req.quantity());
        item.setAmount(req.unitRate() * req.quantity());
        item.setBedChargeFrom(req.bedChargeFrom());
        item.setBedChargeTo(req.bedChargeTo());

        // Fetch item name from service catalog
        serviceCatalogRepo.findById(req.serviceCatalogItemId())
                .ifPresent(sci -> {
                    item.setItemName(sci.getName());
                });

        // Fetch quantitative from charges if present
        chargeRepo.findById(req.serviceCatalogItemId())
                .ifPresent(c -> {
                    item.setQuantitative(c.getQuantitative() != null && c.getQuantitative());
                });

        // Auto-link to pending diagnostic order if one exists for this patient and
        // service
        try {
            diagnosticOrderRepo.findByPatientIdAndPaymentStatusIn(engine.getBill().getPatientId(),
                    List.of(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.ORDERED)).stream()
                    .flatMap(o -> o.getLines().stream())
                    .filter(l -> l.getServiceCatalogItemId().equals(req.serviceCatalogItemId()))
                    .filter(l -> l.getPaymentStatus() == com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.ORDERED)
                    .findFirst()
                    .ifPresent(l -> {
                        item.setDiagnosticOrderId(l.getOrder().getId());
                        item.setDiagnosticOrderLineId(l.getId());
                    });
        } catch (Exception e) {
            log.warn("Failed to auto-link diagnostic order for bill {}: {}", billId, e.getMessage());
        }

        engine.addLineItems(java.util.List.of(item));
        Bill saved = billRepo.save(engine.getBill());

        // Auto-trigger diagnostics for IP immediately
        if (saved.isInpatient() && saved.isDraft()) {
            autoCreateDiagnosticsFromCharges(saved);
            billRepo.save(saved); // Persist linked diagnostic order IDs
        }

        return mapWithPatientInfo(saved);
    }

    @Transactional
    public BillResponse removeChargeLineItem(UUID billId, UUID lineItemId, String reason) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        hydrateDraftBill(bill);
        
        ChargeLineItem line = bill.getChargeLineItems().stream()
                .filter(cli -> lineItemId.equals(cli.getId()))
                .findFirst()
                .orElse(null);

        BillingEngine engine = new BillingEngine(bill, sequencePort, eventPublisher);
        engine.removeLineItem(lineItemId, reason);
        BillResponse resp = mapWithPatientInfo(billRepo.save(engine.getBill()));

        if (line != null && line.getDiagnosticOrderLineId() != null) {
            try {
                var diagnosticService = applicationContext.getBean(com.hms.application.diagnostic.DiagnosticOrderingService.class);
                diagnosticService.cancelOrderLine(line.getDiagnosticOrderLineId());
            } catch (Exception e) {
                log.error("Failed to cancel associated diagnostic order line {}: {}", line.getDiagnosticOrderLineId(), e.getMessage());
            }
        }

        return resp;
    }

    @Transactional
    public BillResponse refund(UUID billId, RefundRequest req) {
        Bill bill = billRepo.findByIdForUpdate(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        hydrateDraftBill(bill);
        BillingEngine engine = new BillingEngine(bill, sequencePort, eventPublisher);

        // OP billing rule: refund only allowed after full settlement and must target
        // specific charges
        if (engine.getBill().isOutpatient()) {
            if (engine.getBill().getBillStatus() != BillStatus.SETTLED) {
                throw new com.hms.exception.BusinessRuleViolationException(
                        "OP refund is only allowed after the bill is fully settled");
            }
            if (req.lineItemIds() == null || req.lineItemIds().isEmpty()) {
                throw new com.hms.exception.BusinessRuleViolationException(
                        "OP refund requires selecting specific charge line items");
            }
        }

        Payment refundPayment = new Payment();
        refundPayment.setAmount(req.amount());
        refundPayment.setPaymentMode(req.paymentMode());
        refundPayment.setNotes(req.notes());
        refundPayment.setSequenceNumber(sequencePort.generateNext(DocumentType.REFUND));
        engine.refundLineItems(req.lineItemIds(), refundPayment);
        return mapWithPatientInfo(billRepo.save(engine.getBill()));
    }

    @Transactional(readOnly = true)
    public BillResponse getBillById(UUID billId) {
        Bill bill = billRepo.findById(billId).orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
        hydrateDraftBill(bill);
        return mapWithPatientInfo(bill);
    }

    /**
     * Hydrates a draft bill with virtual items (bed charges, diagnostics, etc.)
     * and recalculates the total amount.
     */
    private void hydrateDraftBill(Bill bill) {
        if (!bill.isDraft())
            return;

        // Step 1: bed charge auto-calculation (IP only)
        if (bill.isInpatient()) {
            List<ChargeLineItem> bedCharges = bill.getChargeLineItems().stream()
                    .filter(ChargeLineItem::isBedCharge).collect(java.util.stream.Collectors.toList());

            // Enrich bed charge names with type/ward/floor if they are just "Bed: Name"
            bedCharges.forEach(cli -> {
                String currentName = cli.getItemName();
                if (currentName != null && currentName.startsWith("Bed: ") && !currentName.contains("(")) {
                    String bedName = currentName.substring(5).trim();
                    bedRepo.findByName(bedName).ifPresent(bed -> {
                        var category = roomCategoryRepo.findById(bed.getRoomCategoryId());
                        String catName = category.map(com.hms.domain.bed.model.RoomCategory::getName).orElse("");
                        List<String> details = new ArrayList<>();
                        if (!catName.isEmpty())
                            details.add(catName);

                        if (!details.isEmpty()) {
                            cli.setItemName(bed.getName() + " (" + String.join(" | ", details) + ")");
                        }
                    });
                }
            });

            if (!bedCharges.isEmpty() && settingsRegistry.isBedChargeAutomated()) {
                BedChargeCalculator.computeBedCharges(bedCharges, null, true);
            }
        }
        // Step 2: addSales() — inject pharmacy sales as virtual lines (IP only)
        if (bill.isInpatient()) {
            injectPharmacySales(bill);
        }
        // Step 3: inject diagnostic orders as virtual lines (IP and OP)
        injectDiagnosticCharges(bill);
        // Step 4: IP package computation
        if (bill.isInpatient()) {
            IpPackageCalculator.computeIpPackage(bill);
        }

        // Step 5: Recalculate totals for the math to work correctly in BillingEngine
        long total = bill.getChargeLineItems().stream()
                .filter(item -> item.getLineStatus() != com.hms.domain.billing.model.ChargeLineStatus.CANCELLED)
                .mapToLong(com.hms.domain.billing.model.ChargeLineItem::getAmount)
                .sum();
        bill.setBillAmount(total);
    }

    /**
     * Injects diagnostic orders as virtual line items.
     */
    private void injectDiagnosticCharges(Bill bill) {
        log.info("Injecting diagnostics for bill {}, encounter {}", bill.getId(), bill.getEncounterId());

        // Using a Set to avoid duplicates if orders are found via both encounter and
        // patient lookups
        Set<com.hms.domain.diagnostic.model.DiagnosticOrder> allOrders = new java.util.HashSet<>();

        if (bill.getEncounterId() != null) {
            allOrders.addAll(diagnosticOrderRepo.findByEncounterId(bill.getEncounterId()));
        }

        // For Inpatients, also include any pending diagnostics for this patient to
        // ensure nothing is missed
        if (bill.getEncounterType() == EncounterType.INPATIENT) {
            allOrders.addAll(diagnosticOrderRepo.findByPatientIdAndPaymentStatusIn(
                    bill.getPatientId(),
                    List.of(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.ORDERED)));
        }

        List<com.hms.domain.diagnostic.model.DiagnosticOrder> orders = new java.util.ArrayList<>(allOrders);
        log.info("Found {} total diagnostic orders for bill {}", orders.size(), bill.getId());

        // Map of already billed diagnostic line IDs to avoid duplicates
        var existingLineIds = bill.getChargeLineItems().stream()
                .map(ChargeLineItem::getDiagnosticOrderLineId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        // Map of already billed service catalog item IDs to prevent duplicating manual unlinked charges
        var existingServiceItemIds = bill.getChargeLineItems().stream()
                .filter(ChargeLineItem::isActive)
                .map(ChargeLineItem::getServiceCatalogItemId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        orders.stream()
                .filter(order -> !order.isCancelled())
                .forEach(order -> order.getLines().stream()
                        .filter(line -> line.getPaymentStatus() == com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.ORDERED)
                        .filter(line -> !existingLineIds.contains(line.getId()))
                        .filter(line -> !existingServiceItemIds.contains(line.getServiceCatalogItemId()))
                        .forEach(line -> {
                            ChargeLineItem virtual = new ChargeLineItem();
                            virtual.setBill(bill);
                            virtual.setServiceCatalogItemId(line.getServiceCatalogItemId());
                            virtual.setDiagnosticOrderId(order.getId());
                            virtual.setDiagnosticOrderLineId(line.getId());

                            // Fetch rate from service catalog based on bill type, fallback to CHARGES table
                            // if missing
                            long rate = resolveRate(bill, line.getServiceCatalogItemId());

                            virtual.setUnitRate(rate);
                            virtual.setQuantity(1);
                            virtual.setAmount(rate);
                            virtual.setItemName(line.getItemName());
                            virtual.setCreatedAt(line.getCreatedAt());
                            bill.getChargeLineItems().add(virtual);
                            bill.addToBillAmount(virtual.getAmount());
                        }));
    }

    private void injectPharmacySales(Bill bill) {
        try {
            var saleRepo = applicationContext
                    .getBean(com.hms.infrastructure.persistence.sales.PharmacySaleJpaRepository.class);

            // Map of already billed pharmacy sale IDs to avoid duplicates
            var existingSaleIds = bill.getChargeLineItems().stream()
                    .map(ChargeLineItem::getPharmacySaleId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());

            List<com.hms.domain.sales.model.PharmacySale> sales;
            if (bill.getEncounterId() != null) {
                sales = saleRepo.findByEncounterId(bill.getEncounterId());
            } else {
                sales = saleRepo.findByPatientId(bill.getPatientId()).stream()
                        .filter(s -> !s.isDraft() && s.getSaleStatus() != com.hms.domain.sales.model.SaleStatus.SETTLED)
                        .toList();
            }
            sales.forEach(sale -> {
                if (existingSaleIds.contains(sale.getId()))
                    return; // Skip duplicates

                sale.getLines().forEach(line -> {
                    ChargeLineItem virtual = new ChargeLineItem();
                    virtual.setBill(bill);
                    virtual.setPharmacySaleId(sale.getId());
                    virtual.setItemName("Pharmacy Sale: " + sale.getSequenceNumber());
                    virtual.setAmount(line.getAmount().longValue());
                    virtual.setQuantity(line.getQuantity());
                    virtual.setUnitRate(line.getUnitRate().longValue());
                    virtual.setCreatedAt(line.getCreatedAt());
                    bill.getChargeLineItems().add(virtual);
                    bill.addToBillAmount(virtual.getAmount());
                });
            });
        } catch (Exception ignored) {
        }
    }

    /** Settle sales as permanent lines at generateBill() for IP. */
    private void settlePharmacySales(Bill bill) {
        if (bill.getEncounterId() == null)
            return;
        try {
            var saleRepo = applicationContext.getBean(
                    com.hms.infrastructure.persistence.sales.PharmacySaleJpaRepository.class);
            saleRepo.findByEncounterId(bill.getEncounterId()).forEach(sale -> sale.getLines().forEach(line -> {
                ChargeLineItem p = new ChargeLineItem();
                p.setBill(bill);
                p.setServiceCatalogItemId(line.getInventoryBatchId());
                p.setItemName("Pharmacy Sale: " + sale.getSequenceNumber());
                p.setAmount(line.getAmount().longValue());
                p.setQuantity(line.getQuantity());
                p.setUnitRate(line.getUnitRate().longValue());
                p.setPharmacySaleId(sale.getId());
                bill.getChargeLineItems().add(p);
                bill.addToBillAmount(line.getAmount().longValue());
            }));
        } catch (Exception e) {
            log.debug("settlePharmacySales: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<BillSummaryResponse> getBillsByPatient(UUID patientId) {
        return billRepo.findAllByPatientId(patientId).stream().map(this::mapWithPatientName).toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void applyBedChargeCalculation(Bill bill, Instant dischargeAt) {
        List<ChargeLineItem> bedCharges = bill.getChargeLineItems().stream()
                .filter(ChargeLineItem::isBedCharge).filter(cli -> cli.getQuantity() == 0)
                .collect(java.util.stream.Collectors.toList());
        if (!bedCharges.isEmpty()) {
            boolean isDailyBilling = settingsRegistry.isBedChargeAutomated();
            bedCharges.forEach(cli -> {
                if (cli.getItemName() == null)
                    cli.setItemName("Bed Charge");
            });
            long bedTotal = BedChargeCalculator.computeBedCharges(bedCharges, dischargeAt, isDailyBilling);
            // Sync bill amount with computed bed charge total
            bedCharges.forEach(cli -> bill.addToBillAmount(cli.getAmount()));
        }
    }

    // ─── Methods added for missing BillController endpoints ──────────────────

    /**
     * PUT /bill/updateCharge — edits a charge line, writes BillDetailModified audit
     */
    @Transactional
    public BillResponse updateChargeLineItem(UUID billId, UUID lineItemId,
            long newRate, int newQty, long discount, String reason) {
        BillingEngine engine = engineFactory.attach(billId);
        engine.updateLineItem(lineItemId, newRate, newQty, discount, reason);

        // Persist BillDetailModified audit record for charge modification history
        ChargeLineItem edited = engine.getBill().getChargeLineItems().stream()
                .filter(cli -> lineItemId.equals(cli.getId()))
                .findFirst().orElse(null);
        if (edited != null) {
            BillDetailModified audit = new BillDetailModified();
            audit.setChargeLineItemId(lineItemId);
            audit.setPreviousRate(edited.getAuditPreviousRate());
            audit.setPreviousQuantity(edited.getAuditPreviousQty());
            audit.setPreviousAmount(edited.getAuditPreviousAmt());
            audit.setReason(reason);
            audit.setModifiedBy(edited.getModifiedBy());
            audit.setModifiedAt(java.time.Instant.now());
            billDetailModifiedRepo.save(audit);
        }

        return mapWithPatientInfo(billRepo.save(engine.getBill()));
    }

    /**
     * PUT /bill/updateBillDetails — insurance disallowance (only this field
     * changes)
     */
    @Transactional
    public void updateDisallowedAmounts(java.util.List<java.util.Map<String, Object>> lines) {
        if (lines == null || lines.isEmpty())
            return;
        for (var line : lines) {
            if (line.get("id") == null || line.get("disallowedAmount") == null)
                continue;
            UUID id = UUID.fromString(line.get("id").toString());
            long amt = Long.parseLong(line.get("disallowedAmount").toString());
            billRepo.findAll().stream()
                    .flatMap(b -> b.getChargeLineItems().stream())
                    .filter(cli -> id.equals(cli.getId()))
                    .findFirst()
                    .ifPresent(cli -> {
                        cli.setDisallowedAmount(amt);
                        billRepo.save(cli.getBill());
                    });
        }
    }

    /** GET /bill/getBillByVisit?visit= */
    @Transactional(readOnly = true)
    public BillResponse getBillByVisit(UUID visitId) {
        return billRepo.findAll().stream()
                .filter(b -> visitId.equals(b.getEncounterId()))
                .findFirst()
                .map(this::mapWithPatientInfo)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill for visit", visitId));
    }

    /** GET /bill/editChargeHistory/{billDetailId} */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> getEditHistory(UUID lineItemId) {
        return billDetailModifiedRepo.findByChargeLineItemId(lineItemId).stream()
                .map(m -> java.util.Map.<String, Object>of(
                        "chargeLineItemId", m.getChargeLineItemId(),
                        "previousAmount", m.getPreviousAmount(),
                        "previousRate", m.getPreviousRate(),
                        "previousQuantity", m.getPreviousQuantity(),
                        "reason", m.getReason() != null ? m.getReason() : "",
                        "modifiedBy", m.getModifiedBy() != null ? m.getModifiedBy() : "",
                        "modifiedAt", m.getModifiedAt()))
                .toList();
    }

    /** GET /bill/removedChargeHistory/{billId} */
    @Transactional(readOnly = true)
    public java.util.List<Object> getRemovedChargeHistory(UUID billId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        return bill.getChargeLineItems().stream()
                .filter(cli -> cli.getLineStatus() == com.hms.domain.billing.model.ChargeLineStatus.CANCELLED)
                .map(cli -> (Object) cli)
                .toList();
    }

    /** GET /bill/billDetailPackages/{packageId}?billId= */
    @Transactional(readOnly = true)
    public java.util.List<Object> getPackageChargeLines(UUID billId, UUID packageId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Bill", billId));
        return bill.getChargeLineItems().stream()
                .filter(cli -> packageId.equals(cli.getPackageGroupId()))
                .map(cli -> (Object) cli)
                .toList();
    }

    /** GET /bill/currentMonthBill/{patientId} */
    @Transactional(readOnly = true)
    public java.util.List<BillSummaryResponse> getCurrentMonthBills(UUID patientId) {
        java.time.LocalDate start = java.time.LocalDate.now().withDayOfMonth(1);
        return billRepo.findAllByPatientId(patientId).stream()
                .filter(b -> b.getBillDate() != null && !b.getBillDate().isBefore(start))
                .map(this::mapWithPatientName)
                .toList();
    }

    /** PUT /bill/addChargeByVisit — for IP_AUTOMATED_OTHER_CHARGE flow */
    @Transactional
    public BillResponse addChargeByVisit(UUID visitId, AddChargeRequest req) {
        Bill bill = billRepo.findAll().stream()
                .filter(b -> visitId.equals(b.getEncounterId()) && b.isDraft())
                .findFirst()
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("Draft bill for visit", visitId));
        return addChargeLineItem(bill.getId(), req);
    }

    /**
     * Ensures a draft bill exists for the given encounter.
     * Called automatically when a bed is allocated or diagnostic is ordered.
     */
    private Optional<Bill> findExistingDraftBill(UUID patientId, UUID encounterId, EncounterType encounterType) {
        return billRepo.findDraftBillsByPatientId(patientId).stream()
                .filter(b -> {
                    if (encounterType == EncounterType.INPATIENT) {
                        return b.getEncounterType() == EncounterType.INPATIENT;
                    }
                    return encounterId.equals(b.getEncounterId());
                })
                .findFirst();
    }

    @Transactional
    public BillResponse ensureDraftBill(UUID patientId, UUID encounterId, EncounterType encounterType,
            UUID providerId) {
        if (encounterId == null)
            return null;
        synchronized (patientId.toString().intern()) {
            Optional<Bill> existing = findExistingDraftBill(patientId, encounterId, encounterType);
            if (existing.isPresent()) {
                updateEncounterHasDraftBill(encounterId);
                return mapWithPatientInfo(existing.get());
            }
            return ensureDraftBill(patientId, encounterId, encounterType, providerId, BillType.CASH, null);
        }
    }

    @Transactional
    public BillResponse ensureDraftBill(UUID patientId, UUID encounterId, EncounterType encounterType,
            UUID providerId, BillType billType, UUID payorId) {
        if (encounterId == null)
            return null;

        synchronized (patientId.toString().intern()) {
            Optional<Bill> existing = findExistingDraftBill(patientId, encounterId, encounterType);

            if (existing.isPresent()) {
                Bill bill = existing.get();
                boolean changed = false;
                if (billType != null && bill.getBillType() != billType) {
                    bill.setBillType(billType);
                    changed = true;
                }
                boolean payorChanged = !Objects.equals(bill.getPayorId(), payorId);
                if (payorChanged) {
                    bill.setPayorId(payorId);
                    changed = true;
                }
                if (changed) {
                    // Re-rate all active non-bed charge line items to reflect the new payor/billType
                    bill.getChargeLineItems().stream()
                            .filter(item -> item.getBedChargeFrom() == null && item.isActive()
                                    && item.getServiceCatalogItemId() != null)
                            .forEach(item -> {
                                long newRate = resolveRate(bill, item.getServiceCatalogItemId());
                                if (newRate > 0 && newRate != item.getUnitRate()) {
                                    log.info("Re-rating {} from {} to {} due to payor/billType change on bill {}",
                                            item.getItemName(), item.getUnitRate(), newRate, bill.getId());
                                    item.setUnitRate(newRate);
                                    item.setAmount(newRate * item.getQuantity());
                                }
                            });
                    billRepo.saveAndFlush(bill);
                }
                updateEncounterHasDraftBill(encounterId);
                return mapWithPatientInfo(bill);
            }

            log.info("Auto-creating draft bill for encounter {} (patient {}) with billType {} and payorId {}", encounterId, patientId, billType, payorId);
            CreateBillRequest req = new CreateBillRequest(
                    patientId,
                    billType != null ? billType : BillType.CASH,
                    encounterType,
                    providerId,
                    encounterId,
                    payorId,
                    null,
                    null);
            BillResponse resp = createBill(req);
            updateEncounterHasDraftBill(encounterId);
            return resp;
        }
    }

    private long resolveRate(Bill bill, UUID serviceCatalogItemId) {
        if (serviceCatalogItemId == null) {
            return 0L;
        }
        return chargeRepo.findById(serviceCatalogItemId)
                .map(charge -> {
                    var tariffs = charge.getTariffs();
                    long r = 0;
                    if (bill.getEncounterType() == EncounterType.INPATIENT) {
                        if (bill.getPayorId() != null) {
                            var payorTariff = tariffs.stream()
                                    .filter(t -> "INSURANCE".equals(t.getBillType())
                                            && bill.getPayorId().equals(t.getPayorId())
                                            && t.getRate() > 0)
                                    .findFirst();
                            if (payorTariff.isPresent()) {
                                r = payorTariff.get().getRate();
                            }
                        }
                        if (r <= 0) {
                            var creditTariff = tariffs.stream()
                                    .filter(t -> "CREDIT".equals(t.getBillType())
                                            && t.getPayorId() == null
                                            && t.getRate() > 0)
                                    .findFirst();
                            if (creditTariff.isPresent()) {
                                r = creditTariff.get().getRate();
                            }
                        }
                    } else {
                        var cashTariff = tariffs.stream()
                                .filter(t -> "CASH".equals(t.getBillType())
                                        && t.getPayorId() == null
                                        && t.getRate() > 0)
                                    .findFirst();
                        if (cashTariff.isPresent()) {
                            r = cashTariff.get().getRate();
                        }
                    }
                    if (r <= 0 && !tariffs.isEmpty()) {
                        // Fallback to cash rate if no credit rate is configured but cash is configured
                        var cashFallback = tariffs.stream()
                                .filter(t -> "CASH".equals(t.getBillType())
                                        && t.getPayorId() == null
                                        && t.getRate() > 0)
                                .findFirst();
                        if (cashFallback.isPresent()) {
                            r = cashFallback.get().getRate();
                        } else {
                            r = tariffs.get(0).getRate();
                        }
                    }
                    return r;
                }).orElseGet(() -> {
                    return serviceCatalogRepo.findById(serviceCatalogItemId)
                            .map(item -> {
                                var tiers = item.getPricingTiers();
                                var tier = tiers.stream()
                                        .filter(t -> t.getBillType() == bill.getBillType()
                                                && t.getUnitRate() > 0)
                                        .findFirst();
                                if (tier.isEmpty() && bill.getBillType() != BillType.CASH) {
                                    tier = tiers.stream()
                                            .filter(t -> t.getBillType() == BillType.CASH)
                                            .findFirst();
                                }
                                return tier.map(com.hms.domain.catalog.model.PricingTier::getUnitRate)
                                        .orElse(0L);
                            }).orElse(0L);
                });
    }

    private void updateEncounterHasDraftBill(UUID encounterId) {
        try {
            encounterRepo.findById(encounterId).ifPresent(e -> {
                if (!e.isHasDraftBill()) {
                    e.setHasDraftBill(true);
                    encounterRepo.save(e);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to update hasDraftBill flag for encounter {}: {}", encounterId, e.getMessage());
        }
    }
}
