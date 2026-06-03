package com.hms.application.diagnostic;

import com.hms.api.diagnostic.request.PlaceOrderRequest;
import com.hms.api.diagnostic.request.RecordResultRequest;
import com.hms.api.diagnostic.response.DiagnosticOrderResponse;
import com.hms.domain.diagnostic.model.*;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.domain.billing.model.DocumentType;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.DiagnosticMapper;
import com.hms.infrastructure.persistence.diagnostic.DiagnosticOrderJpaRepository;
import com.hms.infrastructure.persistence.diagnostic.SpecimenCollectionJpaRepository;
import com.hms.domain.diagnostic.model.SpecimenCollection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticOrderingService {

    private final DiagnosticOrderJpaRepository orderRepo;
    private final DiagnosticMapper diagnosticMapper;
    private final SequenceNumberPort sequenceNumberPort;
    private final SpecimenCollectionJpaRepository specimenCollectionRepo;
    private final com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository encounterRepo;
    private final com.hms.infrastructure.persistence.patient.PatientJpaRepository patientRepo;
    private final com.hms.infrastructure.sequence.NumberSequenceJpaRepository numberSequenceRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hms.infrastructure.persistence.diagtemplate.DiagnosticTemplateJpaRepository templateRepo;

    /**
     * Places a diagnostic order — one order per type (LAB or RADIOLOGY) per
     * encounter.
     * Generates a sequence number automatically.
     */
    @Transactional
    public DiagnosticOrderResponse placeOrder(PlaceOrderRequest req) {
        DiagnosticOrder order = new DiagnosticOrder();
        UUID encounterId = req.encounterId();

        // Auto-link to active IP encounter if missing
        if (encounterId == null && req.patientId() != null) {
            encounterId = encounterRepo.findActiveInpatientByPatientId(req.patientId()).stream().findFirst()
                    .map(com.hms.domain.encounter.model.ClinicalEncounter::getId)
                    .orElse(null);
        }

        order.setEncounterId(encounterId);
        order.setPatientId(req.patientId());
        order.setProviderId(req.providerId());
        order.setOrderDate(LocalDate.now());
        order.setPaymentStatus(DiagnosticPaymentStatus.ORDERED);
        order.setTestStatus(DiagnosticTestStatus.PENDING);
        order.setDiagnosticType(req.diagnosticType());

        // Generate sequence number (LAB_ORDER or IP_ORDER based on type)
        DocumentType docType = req.diagnosticType() == DiagnosticType.LAB
                ? DocumentType.LAB_ORDER
                : DocumentType.IP_ORDER;

        // CHECK FOR EXISTING DRAFT ORDER TO APPEND (PREVENT CLUTTER)
        if (encounterId != null && req.billId() != null) {
            List<DiagnosticOrder> existing = orderRepo.findByEncounterIdAndBillIdAndDiagnosticTypeAndPaymentStatus(encounterId, req.billId(), req.diagnosticType(), DiagnosticPaymentStatus.ORDERED);
            if (existing.isEmpty()) {
                existing = orderRepo.findByEncounterIdAndBillIdAndDiagnosticTypeAndPaymentStatus(encounterId, req.billId(), req.diagnosticType(), DiagnosticPaymentStatus.BILLED);
            }
            
            if (!existing.isEmpty()) {
                DiagnosticOrder existingOrder = existing.get(0); // Take the most recent one
                List<DiagnosticOrderLine> lines = req.lines().stream().map(l -> {
                    DiagnosticOrderLine line = new DiagnosticOrderLine();
                    line.setOrder(existingOrder);
                    line.setServiceCatalogItemId(l.serviceCatalogItemId());
                    
                    String itemName = l.itemName();
                    if ((itemName == null || itemName.isBlank()) && l.serviceCatalogItemId() != null) {
                        List<com.hms.domain.diagnostic.model.DiagnosticTemplate> templates = templateRepo.findByChargeId(l.serviceCatalogItemId());
                        if (!templates.isEmpty()) {
                            itemName = templates.get(0).getName();
                        }
                    }
                    line.setItemName(itemName);

                    UUID specimenId = l.specimenId();
                    if (specimenId == null && l.serviceCatalogItemId() != null) {
                        List<com.hms.domain.diagnostic.model.DiagnosticTemplate> templates = templateRepo.findByChargeId(l.serviceCatalogItemId());
                        if (!templates.isEmpty()) {
                            specimenId = templates.get(0).getSpecimenId();
                        }
                    }
                    line.setSpecimenId(specimenId);

                    line.setInstruction(l.instruction());
                    line.setPaymentStatus(existingOrder.getPaymentStatus() == DiagnosticPaymentStatus.BILLED ? DiagnosticPaymentStatus.BILLED : DiagnosticPaymentStatus.ORDERED);
                    line.setTestStatus(DiagnosticTestStatus.PENDING);
                    return line;
                }).toList();
                existingOrder.getLines().addAll(lines);
                return mapWithNames(orderRepo.save(existingOrder));
            }
        }

        order.setSequenceNumber(sequenceNumberPort.generateNext(docType));
        order.setBillId(req.billId());

        // Build order lines
        List<DiagnosticOrderLine> lines = req.lines().stream().map(l -> {
            DiagnosticOrderLine line = new DiagnosticOrderLine();
            line.setOrder(order);
            line.setServiceCatalogItemId(l.serviceCatalogItemId());
            
            String itemName = l.itemName();
            if ((itemName == null || itemName.isBlank()) && l.serviceCatalogItemId() != null) {
                List<com.hms.domain.diagnostic.model.DiagnosticTemplate> templates = templateRepo.findByChargeId(l.serviceCatalogItemId());
                if (!templates.isEmpty()) {
                    itemName = templates.get(0).getName();
                }
            }
            line.setItemName(itemName);

            UUID specimenId = l.specimenId();
            if (specimenId == null && l.serviceCatalogItemId() != null) {
                List<com.hms.domain.diagnostic.model.DiagnosticTemplate> templates = templateRepo.findByChargeId(l.serviceCatalogItemId());
                if (!templates.isEmpty()) {
                    specimenId = templates.get(0).getSpecimenId();
                }
            }
            line.setSpecimenId(specimenId);

            line.setInstruction(l.instruction());
            line.setPaymentStatus(DiagnosticPaymentStatus.ORDERED);
            line.setTestStatus(DiagnosticTestStatus.PENDING);
            return line;
        }).toList();

        order.getLines().addAll(lines);
        DiagnosticOrder saved = orderRepo.save(order);

        // Auto-create bill for IP diagnostics
        if (encounterId != null) {
            final UUID finalEncounterId = encounterId;
            try {
                encounterRepo.findById(encounterId).ifPresent(enc -> {
                    if (enc.isInpatient()) {
                        var billingService = applicationContext.getBean(com.hms.application.billing.BillingOperationsService.class);
                        billingService.ensureDraftBill(enc.getPatientId(), finalEncounterId, enc.getEncounterType(), enc.getPrimaryProviderId());
                    }
                });
            } catch (Exception e) {
                log.error("Failed to trigger billing for IP diagnostics: {}", e.getMessage());
            }
        }

        return mapWithNames(saved);
    }

    /**
     * Records a result for a single diagnostic order line.
     * Auto-advances the parent order to RESULTED when all lines have results.
     */
    @Transactional
    public DiagnosticOrderResponse recordResult(UUID orderId, RecordResultRequest req) {
        DiagnosticOrder order = findOrThrow(orderId);

        DiagnosticOrderLine line = order.getLines().stream()
                .filter(l -> l.getId().equals(req.lineId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "DiagnosticOrderLine " + req.lineId() + " not found on order " + orderId));

        line.recordResult(req.resultValue(), req.resultUnit(), req.referenceRange());

        // Auto-advance order status when all non-cancelled lines have results
        boolean allResulted = order.getLines().stream()
                .filter(l -> l.getTestStatus() != DiagnosticTestStatus.CANCELLED)
                .allMatch(DiagnosticOrderLine::hasResult);

        if (allResulted) {
            order.markResulted();
        }

        DiagnosticOrder saved = orderRepo.save(order);
        return mapWithNames(saved);
    }

    /**
     * Mark the entire order as billed (called after charge lines added to bill).
     */
    @Transactional
    public DiagnosticOrderResponse markBilled(UUID orderId) {
        DiagnosticOrder order = findOrThrow(orderId);
        order.markBilled();
        order.getLines().stream()
                .filter(l -> l.getPaymentStatus() == DiagnosticPaymentStatus.ORDERED
                          || l.getPaymentStatus() == DiagnosticPaymentStatus.PART_PAID)
                .forEach(l -> l.setPaymentStatus(DiagnosticPaymentStatus.BILLED));
        DiagnosticOrder saved = orderRepo.save(order);
        return mapWithNames(saved);
    }

    /**
     * Mark the order as PART_PAID (OP partial payment received — appears in diagnostics).
     */
    @Transactional
    public DiagnosticOrderResponse markPartPaid(UUID orderId) {
        DiagnosticOrder order = findOrThrow(orderId);
        order.markPartPaid();
        DiagnosticOrder saved = orderRepo.save(order);
        return mapWithNames(saved);
    }

    public DiagnosticOrderResponse cancelOrder(UUID orderId) {
        DiagnosticOrder order = findOrThrow(orderId);
        order.cancel();
        DiagnosticOrder saved = orderRepo.save(order);
        return mapWithNames(saved);
    }

    @Transactional(readOnly = true)
    public List<DiagnosticOrderResponse> getByEncounter(UUID encounterId) {
        return orderRepo.findByEncounterId(encounterId).stream()
                .map(this::mapWithNames)
                .filter(resp -> !resp.lines().isEmpty())
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DiagnosticOrderResponse> getByPatient(UUID patientId, Pageable pageable) {
        return orderRepo.findByPatientId(patientId, pageable)
                .map(this::mapWithNames);
    }

    @Transactional(readOnly = true)
    public DiagnosticOrderResponse getById(UUID orderId) {
        return mapWithNames(findOrThrow(orderId));
    }

    @Transactional(readOnly = true)
    public List<DiagnosticOrderResponse> getPendingOrders(DiagnosticType type, LocalDate from, LocalDate to) {
        List<DiagnosticOrder> orders = orderRepo.findPendingByTypeAndDateRange(type, from, to);

        return orders.stream()
                .filter(order -> {
                    // Cancelled orders are hidden
                    if (order.getTestStatus() == DiagnosticTestStatus.CANCELLED) {
                        return false;
                    }
                    // Return true for all non-cancelled orders
                    return true;
                })
                .map(this::mapWithNames)
                .filter(resp -> !resp.lines().isEmpty())
                .toList();
    }

    private DiagnosticOrderResponse mapWithNames(DiagnosticOrder order) {
        DiagnosticOrderResponse resp = diagnosticMapper.toResponse(order);
        List<com.hms.api.diagnostic.response.DiagnosticOrderLineResponse> filteredLines = resp.lines().stream()
                .filter(l -> l.testStatus() != com.hms.domain.diagnostic.model.DiagnosticTestStatus.CANCELLED)
                .toList();

        String name = resp.patientName();
        String number = resp.patientNumber();
        String gender = resp.patientGender();
        String age = resp.patientAge();

        if (name == null || number == null) {
            var patientOpt = patientRepo.findById(order.getPatientId());
            if (patientOpt.isPresent()) {
                var p = patientOpt.get();
                name = p.computeFullName();
                number = numberSequenceRepo.findById(p.getId())
                        .map(com.hms.infrastructure.sequence.NumberSequenceEntity::getValue)
                        .orElse(null);
                gender = p.getGender() != null ? p.getGender().name() : null;
                age = p.computeAge();
            }
        }

        String encounterType = null;
        if (order.getEncounterId() != null) {
            encounterType = encounterRepo.findById(order.getEncounterId())
                    .map(e -> e.getEncounterType() == com.hms.domain.billing.model.EncounterType.INPATIENT ? "IP" : "OP")
                    .orElse(null);
        }

        return new DiagnosticOrderResponse(
                resp.id(), resp.encounterId(), resp.patientId(), resp.providerId(),
                resp.diagnosticType(), resp.sequenceNumber(), resp.orderDate(),
                resp.paymentStatus(), resp.testStatus(), resp.billed(), name, number, gender, age, encounterType, filteredLines);
    }

    private DiagnosticOrder findOrThrow(UUID id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DiagnosticOrder", id));
    }

    @Transactional
    public void cancelOrderLine(java.util.UUID orderLineId) {
        orderRepo.findByLineId(orderLineId).ifPresent(order -> {
            order.getLines().stream()
                 .filter(l -> l.getId().equals(orderLineId))
                 .findFirst()
                 .ifPresent(com.hms.domain.diagnostic.model.DiagnosticOrderLine::cancel);
            
            boolean allCancelled = order.getLines().stream()
                 .allMatch(l -> l.getTestStatus() == com.hms.domain.diagnostic.model.DiagnosticTestStatus.CANCELLED);
            if (allCancelled) {
                order.setTestStatus(com.hms.domain.diagnostic.model.DiagnosticTestStatus.CANCELLED);
            }
            orderRepo.save(order);
        });
    }

    /**
     * POST /diagnostics/recordSpecimenCollection
     * Records specimen collection. Generates PrefixType.SAMPLE number.
     * Throws if SAMPLE prefix not configured: 'Please create Prefix for
     * SpecimenCollection'
     */
    @Transactional
    public SpecimenCollection recordSpecimenCollection(UUID diagnosticId, UUID specimenId, UUID orderLineId, String notes) {
        SpecimenCollection sc = new SpecimenCollection();
        sc.setDiagnosticId(diagnosticId);
        sc.setSpecimenId(specimenId);
        sc.setOrderLineId(orderLineId);
        sc.setCollectionNotes(notes);
        sc.setCollectedAt(java.time.Instant.now());
        try {
            sc.setSampleNumber(sequenceNumberPort.generateNext(DocumentType.SAMPLE));
        } catch (Exception e) {
            throw new com.hms.exception.BusinessRuleViolationException(
                    "Please create Prefix for SpecimenCollection");
        }
        
        // Update the order line test status to RECORDED
        if (orderLineId != null) {
            orderRepo.findByLineId(orderLineId).ifPresent(order -> {
                order.getLines().stream()
                     .filter(l -> l.getId().equals(orderLineId))
                     .findFirst()
                     .ifPresent(l -> {
                         if (l.getTestStatus() == DiagnosticTestStatus.PENDING) {
                             l.setTestStatus(DiagnosticTestStatus.RECORDED);
                         }
                     });

                // Advance order-level test status to RECORDED when all non-cancelled lines are at least RECORDED
                boolean allRecordedOrBeyond = order.getLines().stream()
                        .filter(l -> l.getTestStatus() != DiagnosticTestStatus.CANCELLED)
                        .allMatch(l -> l.getTestStatus() == DiagnosticTestStatus.RECORDED
                                    || l.getTestStatus() == DiagnosticTestStatus.RESULTED);
                if (allRecordedOrBeyond && order.getTestStatus() == DiagnosticTestStatus.PENDING) {
                    order.setTestStatus(DiagnosticTestStatus.RECORDED);
                }
                orderRepo.save(order);
            });
        }
        
        return specimenCollectionRepo.save(sc);
    }

    /**
     * GET /diagnostics/getSpecimenCollection?diagnosticsId=
     * Returns specimen collections for a diagnostic.
     */
    @Transactional(readOnly = true)
    public List<SpecimenCollection> getSpecimenCollections(UUID diagnosticId) {
        return specimenCollectionRepo.findByDiagnosticId(diagnosticId);
    }

    /**
     * GET /diagnostics/getUnbilledDiagnosticOrders?patientId=
     * Returns unbilled diagnostic orders for a patient.
     * Used by billing module to link diagnostics to a bill.
     */
    @Transactional(readOnly = true)
    public List<DiagnosticOrderResponse> getUnbilledOrders(UUID patientId) {
        return getByPatient(patientId, org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent().stream()
                .filter(o -> !o.billed())
                .toList();
    }

    /**
     * GET /diagnostics/getRadiologyTests?diagnosticId=
     */
    @Transactional(readOnly = true)
    public List<DiagnosticOrderResponse> getRadiologyTests(UUID diagnosticId) {
        return List.of(getById(diagnosticId));
    }

    /**
     * GET /diagnostics/getRadiologyTests/visit/{visitId}
     */
    @Transactional(readOnly = true)
    public List<DiagnosticOrderResponse> getRadiologyTestsByVisit(UUID visitId) {
        return getByEncounter(visitId).stream()
                .filter(o -> "RADIOLOGY".equals(o.diagnosticType() != null
                        ? o.diagnosticType().toString()
                        : ""))
                .toList();
    }

    /**
     * GET /diagnostics/getDiagnosticDetailsByDiagnosticDetailId
     */
    @Transactional(readOnly = true)
    public DiagnosticOrderResponse getDiagnosticDetailsByDetailId(UUID detailId,
            String type, UUID chargeId) {
        return getById(detailId);
    }

    /**
     * POST /diagnostics/autoCreateFromCharge — called by BillingOperationsService
     * when a DIAGNOSTICS-category charge is paid.
     */
    @Transactional
    public void autoCreateFromCharge(UUID patientId, UUID encounterId, UUID chargeId, DiagnosticType type,
            String itemName) {
        if (patientId == null || chargeId == null)
            return;
        try {
            // Auto-creation from billing charge — best-effort, never blocks billing
            var orderLine = new com.hms.api.diagnostic.request.PlaceOrderRequest.OrderLineRequest(
                    chargeId, itemName, null, "Auto-created from bill payment");
            var req = new com.hms.api.diagnostic.request.PlaceOrderRequest(
                    encounterId, patientId, null,
                    type != null ? type : DiagnosticType.LAB,
                    null, // No specific bill ID for this legacy trigger
                    java.util.List.of(orderLine));
            var savedOrder = placeOrder(req);
            markBilled(savedOrder.id());
        } catch (Exception ignored) {
            // Auto-creation is best-effort — never block billing
        }
    }
}
