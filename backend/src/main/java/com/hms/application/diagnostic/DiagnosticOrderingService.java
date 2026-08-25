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

    @org.springframework.beans.factory.annotation.Autowired
    private com.hms.infrastructure.persistence.catalog.ServiceCatalogItemJpaRepository serviceCatalogRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hms.infrastructure.persistence.charge.ChargeJpaRepository chargeRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hms.infrastructure.persistence.billing.BillJpaRepository billRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private com.hms.infrastructure.persistence.billing.ChargeLineItemJpaRepository chargeLineItemRepo;

    /**
     * Places a diagnostic order — one order per type (LAB or RADIOLOGY) per
     * encounter.
     * Generates a sequence number automatically.
     */
    @Transactional
    public DiagnosticOrderResponse placeOrder(PlaceOrderRequest req) {
        UUID encounterId = req.encounterId();

        // Auto-link to active IP encounter if missing
        if (encounterId == null && req.patientId() != null) {
            encounterId = encounterRepo.findActiveInpatientByPatientId(req.patientId()).stream().findFirst()
                    .map(com.hms.domain.encounter.model.ClinicalEncounter::getId)
                    .orElse(null);
        }

        DiagnosticOrder order = null;
        if (encounterId != null) {
            List<DiagnosticOrder> existing = orderRepo.findByEncounterId(encounterId);
            order = existing.stream()
                    .filter(o -> o.getDiagnosticType() == req.diagnosticType()
                            && o.getPaymentStatus() == DiagnosticPaymentStatus.ORDERED
                            && (req.billId() == null || req.billId().equals(o.getBillId()))
                            && java.time.LocalDate.now().equals(o.getOrderDate()))
                    .findFirst()
                    .orElse(null);
        }

        if (order == null) {
            order = new DiagnosticOrder();
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

            order.setSequenceNumber(sequenceNumberPort.generateNext(docType));
            order.setBillId(req.billId());
        } else {
            // If order already exists, ensure the testStatus is PENDING if we are adding new tests
            order.setTestStatus(DiagnosticTestStatus.PENDING);
            if (req.providerId() != null) {
                order.setProviderId(req.providerId());
            }
        }

        final DiagnosticOrder finalOrder = order;

        // Build order lines
        List<DiagnosticOrderLine> lines = req.lines().stream().map(l -> {
            DiagnosticOrderLine line = new DiagnosticOrderLine();
            line.setOrder(finalOrder);
            line.setServiceCatalogItemId(l.serviceCatalogItemId());

            // Resolve the DiagnosticTemplate for this service catalog item.
            // service_catalog_items and charges are separate tables with different IDs,
            // so we bridge via name: serviceCatalogItem.name → charge.name → template.chargeId
            List<com.hms.domain.diagnostic.model.DiagnosticTemplate> templates = resolveTemplatesByServiceCatalogItemId(l.serviceCatalogItemId());

            String itemName = l.itemName();
            if ((itemName == null || itemName.isBlank()) && !templates.isEmpty()) {
                itemName = templates.get(0).getName();
            }
            line.setItemName(itemName);

            UUID specimenId = l.specimenId();
            if (specimenId == null && !templates.isEmpty()) {
                specimenId = templates.get(0).getSpecimenId();
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
        syncDiagnosticPaymentStatus(order);

        DiagnosticOrderResponse resp = diagnosticMapper.toResponse(order);
        List<com.hms.api.diagnostic.response.DiagnosticOrderLineResponse> filteredLines = resp.lines().stream()
                .filter(l -> l.testStatus() != com.hms.domain.diagnostic.model.DiagnosticTestStatus.CANCELLED)
                .map(l -> {
                    if (order.getLines() != null) {
                        for (var entityLine : order.getLines()) {
                            if (entityLine.getId().equals(l.id()) && entityLine.getPaymentStatus() != l.paymentStatus()) {
                                return new com.hms.api.diagnostic.response.DiagnosticOrderLineResponse(
                                    l.id(), l.serviceCatalogItemId(), l.itemName(),
                                    l.specimenId(), l.specimenName(), l.instruction(), entityLine.getPaymentStatus(), l.testStatus(),
                                    l.resultValue(), l.resultUnit(), l.referenceRange(),
                                    l.resultRecordedAt(), l.hasResult()
                                );
                            }
                        }
                    }
                    return l;
                })
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
                order.getPaymentStatus(), resp.testStatus(), resp.billed(), name, number, gender, age, encounterType, filteredLines);
    }

    private void syncDiagnosticPaymentStatus(DiagnosticOrder order) {
        if (order == null) return;
        boolean modified = false;

        UUID bId = order.getBillId();
        if (bId == null && chargeLineItemRepo != null) {
            try {
                var items = chargeLineItemRepo.findByDiagnosticOrderId(order.getId());
                if (items != null && !items.isEmpty()) {
                    bId = items.get(0).getBill() != null ? items.get(0).getBill().getId() : null;
                    if (bId != null) {
                        order.setBillId(bId);
                        modified = true;
                    }
                }
            } catch (Exception e) {}
        }

        if (bId == null || billRepo == null) {
            if (modified) { try { orderRepo.save(order); } catch (Exception ex) {} }
            return;
        }

        var billOpt = billRepo.findById(bId);
        if (billOpt.isEmpty()) {
            if (modified) { try { orderRepo.save(order); } catch (Exception ex) {} }
            return;
        }

        var bill = billOpt.get();
        var billStatus = bill.getBillStatus();

        // Case 1: Bill is CANCELLED — all diagnostic lines should be CANCELLED
        if (billStatus == com.hms.domain.billing.model.BillStatus.CANCELLED) {
            if (order.getPaymentStatus() != DiagnosticPaymentStatus.CANCELLED) {
                order.setPaymentStatus(DiagnosticPaymentStatus.CANCELLED);
                modified = true;
            }
            if (order.getLines() != null) {
                for (var l : order.getLines()) {
                    if (l.getPaymentStatus() != DiagnosticPaymentStatus.CANCELLED) {
                        l.setPaymentStatus(DiagnosticPaymentStatus.CANCELLED);
                        modified = true;
                    }
                }
            }
        }
        // Case 2: Bill is REFUNDED — all diagnostic lines should be REFUNDED
        else if (billStatus == com.hms.domain.billing.model.BillStatus.REFUNDED) {
            if (order.getPaymentStatus() != DiagnosticPaymentStatus.REFUNDED) {
                order.setPaymentStatus(DiagnosticPaymentStatus.REFUNDED);
                modified = true;
            }
            if (order.getLines() != null) {
                for (var l : order.getLines()) {
                    if (l.getPaymentStatus() != DiagnosticPaymentStatus.REFUNDED) {
                        l.setPaymentStatus(DiagnosticPaymentStatus.REFUNDED);
                        modified = true;
                    }
                }
            }
        }
        // Case 3: Partial refund — check each charge line item individually
        else if (bill.getServiceRefundTotal() > 0 || bill.getRefundTotal() > 0) {
            var billItems = bill.getChargeLineItems() != null ? bill.getChargeLineItems() : java.util.Collections.<com.hms.domain.billing.model.ChargeLineItem>emptyList();
            boolean anyRefunded = false;
            boolean allRefunded = true;

            if (order.getLines() != null) {
                for (var l : order.getLines()) {
                    DiagnosticPaymentStatus effectiveStatus = null;

                    // Match this diagnostic line to its charge line item
                    for (var item : billItems) {
                        boolean isSameLine = (item.getDiagnosticOrderLineId() != null && item.getDiagnosticOrderLineId().equals(l.getId())) ||
                                             (item.getDiagnosticOrderId() != null && item.getDiagnosticOrderId().equals(order.getId()) &&
                                              item.getServiceCatalogItemId() != null && item.getServiceCatalogItemId().equals(l.getServiceCatalogItemId()));
                        if (isSameLine) {
                            if (item.getLineStatus() == com.hms.domain.billing.model.ChargeLineStatus.REFUNDED) {
                                effectiveStatus = DiagnosticPaymentStatus.REFUNDED;
                            } else if (item.getLineStatus() == com.hms.domain.billing.model.ChargeLineStatus.CANCELLED) {
                                effectiveStatus = DiagnosticPaymentStatus.CANCELLED;
                            } else {
                                effectiveStatus = DiagnosticPaymentStatus.BILLED;
                            }
                            break;
                        }
                    }

                    if (effectiveStatus == DiagnosticPaymentStatus.REFUNDED) {
                        anyRefunded = true;
                        if (l.getPaymentStatus() != DiagnosticPaymentStatus.REFUNDED) {
                            l.setPaymentStatus(DiagnosticPaymentStatus.REFUNDED);
                            modified = true;
                        }
                    } else if (effectiveStatus == DiagnosticPaymentStatus.CANCELLED) {
                        allRefunded = false;
                        if (l.getPaymentStatus() != DiagnosticPaymentStatus.CANCELLED) {
                            l.setPaymentStatus(DiagnosticPaymentStatus.CANCELLED);
                            modified = true;
                        }
                    } else {
                        // This line's charge is still active — reset if incorrectly set
                        allRefunded = false;
                        if (l.getPaymentStatus() == DiagnosticPaymentStatus.REFUNDED || l.getPaymentStatus() == DiagnosticPaymentStatus.CANCELLED) {
                            l.setPaymentStatus(DiagnosticPaymentStatus.BILLED);
                            modified = true;
                        }
                    }
                }
            }

            // Set order-level status
            if (allRefunded && anyRefunded) {
                if (order.getPaymentStatus() != DiagnosticPaymentStatus.REFUNDED) {
                    order.setPaymentStatus(DiagnosticPaymentStatus.REFUNDED);
                    modified = true;
                }
            } else if (anyRefunded) {
                if (order.getPaymentStatus() != DiagnosticPaymentStatus.PART_PAID) {
                    order.setPaymentStatus(DiagnosticPaymentStatus.PART_PAID);
                    modified = true;
                }
            } else {
                // No lines refunded — reset order status if incorrectly set
                if (order.getPaymentStatus() == DiagnosticPaymentStatus.REFUNDED || order.getPaymentStatus() == DiagnosticPaymentStatus.CANCELLED) {
                    order.setPaymentStatus(DiagnosticPaymentStatus.BILLED);
                    modified = true;
                }
            }
        }
        // Case 4: Bill has no refunds — reset any incorrectly set statuses
        else {
            if (order.getPaymentStatus() == DiagnosticPaymentStatus.REFUNDED || order.getPaymentStatus() == DiagnosticPaymentStatus.CANCELLED) {
                order.setPaymentStatus(DiagnosticPaymentStatus.BILLED);
                modified = true;
            }
            if (order.getLines() != null) {
                for (var l : order.getLines()) {
                    if (l.getPaymentStatus() == DiagnosticPaymentStatus.REFUNDED || l.getPaymentStatus() == DiagnosticPaymentStatus.CANCELLED) {
                        l.setPaymentStatus(DiagnosticPaymentStatus.BILLED);
                        modified = true;
                    }
                }
            }
        }

        if (modified) {
            try {
                orderRepo.save(order);
            } catch (Exception ex) {
                log.warn("Failed to save synced diagnostic order status: {}", ex.getMessage());
            }
        }
    }

    private DiagnosticOrder findOrThrow(UUID id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DiagnosticOrder", id));
    }

    @Transactional
    public void refundOrder(UUID orderId) {
        if (orderId == null) return;
        orderRepo.findById(orderId).ifPresent(order -> {
            order.setPaymentStatus(DiagnosticPaymentStatus.REFUNDED);
            if (order.getLines() != null) {
                order.getLines().forEach(l -> l.setPaymentStatus(DiagnosticPaymentStatus.REFUNDED));
            }
            orderRepo.save(order);
        });
    }

    @Transactional
    public void refundByBillId(UUID billId) {
        if (billId == null) return;
        List<DiagnosticOrder> orders = orderRepo.findByBillId(billId);
        for (DiagnosticOrder order : orders) {
            syncDiagnosticPaymentStatus(order);
        }
    }

    @Transactional
    public void cancelByBillId(UUID billId) {
        if (billId == null) return;
        List<DiagnosticOrder> orders = orderRepo.findByBillId(billId);
        for (DiagnosticOrder order : orders) {
            syncDiagnosticPaymentStatus(order);
        }
    }

    @Transactional
    public void cancelOrderLine(java.util.UUID orderLineId) {
        orderRepo.findByLineId(orderLineId).ifPresent(order -> {
            order.getLines().stream()
                 .filter(l -> l.getId().equals(orderLineId))
                 .findFirst()
                 .ifPresent(l -> {
                     l.cancel();
                     l.setPaymentStatus(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.CANCELLED);
                 });
            
            boolean allCancelled = order.getLines().stream()
                 .allMatch(l -> l.getTestStatus() == com.hms.domain.diagnostic.model.DiagnosticTestStatus.CANCELLED);
            if (allCancelled) {
                order.setTestStatus(com.hms.domain.diagnostic.model.DiagnosticTestStatus.CANCELLED);
                order.setPaymentStatus(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.CANCELLED);
            }
            orderRepo.save(order);
        });
    }

    @Transactional
    public void refundOrderLine(java.util.UUID orderLineId) {
        orderRepo.findByLineId(orderLineId).ifPresent(order -> {
            order.getLines().stream()
                 .filter(l -> l.getId().equals(orderLineId))
                 .findFirst()
                 .ifPresent(l -> l.setPaymentStatus(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.REFUNDED));

            boolean allRefunded = order.getLines().stream()
                 .allMatch(l -> l.getPaymentStatus() == com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.REFUNDED);
            if (allRefunded) {
                order.setPaymentStatus(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.REFUNDED);
            } else {
                order.setPaymentStatus(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.PART_PAID);
            }
            orderRepo.save(order);
        });
    }

    @Transactional
    public void cancelOrderLinePayment(java.util.UUID orderLineId) {
        orderRepo.findByLineId(orderLineId).ifPresent(order -> {
            order.getLines().stream()
                 .filter(l -> l.getId().equals(orderLineId))
                 .findFirst()
                 .ifPresent(l -> l.setPaymentStatus(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.CANCELLED));

            boolean allCancelled = order.getLines().stream()
                 .allMatch(l -> l.getPaymentStatus() == com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.CANCELLED);
            if (allCancelled) {
                order.setPaymentStatus(com.hms.domain.diagnostic.model.DiagnosticPaymentStatus.CANCELLED);
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
        DiagnosticOrder order = findOrThrow(diagnosticId);

        SpecimenCollection sc = new SpecimenCollection();
        sc.setDiagnosticId(diagnosticId);
        sc.setSpecimenId(specimenId);
        sc.setOrderLineId(orderLineId);
        sc.setCollectionNotes(notes);
        sc.setCollectedAt(java.time.Instant.now());
        sc.setTenantId(order.getTenantId());
        sc.setBranchId(order.getBranchId());
        try {
            sc.setSampleNumber(sequenceNumberPort.generateNext(DocumentType.SAMPLE));
        } catch (Exception e) {
            throw new com.hms.exception.BusinessRuleViolationException(
                    "Please create Prefix for SpecimenCollection");
        }
        
        // Update the order line test status to RECORDED
        if (orderLineId != null) {
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

    /**
     * Resolves DiagnosticTemplates for a given serviceCatalogItemId.
     *
     * The service_catalog_items and charges tables have different IDs for the same
     * test item. DiagnosticTemplate.chargeId references the charges table, while
     * DiagnosticOrderLine.serviceCatalogItemId references service_catalog_items.
     *
     * This method bridges the gap: looks up the service catalog item's name,
     * finds the matching charge by name, then looks up templates by charge ID.
     */
    private List<com.hms.domain.diagnostic.model.DiagnosticTemplate> resolveTemplatesByServiceCatalogItemId(UUID serviceCatalogItemId) {
        if (serviceCatalogItemId == null) return List.of();

        // Step 1: Try direct lookup (works when chargeId == serviceCatalogItemId, e.g. legacy data)
        List<com.hms.domain.diagnostic.model.DiagnosticTemplate> templates = templateRepo.findByChargeId(serviceCatalogItemId);
        if (!templates.isEmpty()) return templates;

        // Step 2: Bridge via name — serviceCatalogItem.name → charge.name → template.chargeId
        try {
            var catalogItemOpt = serviceCatalogRepo.findById(serviceCatalogItemId);
            if (catalogItemOpt.isPresent()) {
                String itemName = catalogItemOpt.get().getName();
                if (itemName != null && !itemName.isBlank()) {
                    List<com.hms.domain.charge.model.Charge> charges = chargeRepo.findByNameIgnoreCase(itemName);
                    for (com.hms.domain.charge.model.Charge charge : charges) {
                        templates = templateRepo.findByChargeId(charge.getId());
                        if (!templates.isEmpty()) return templates;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve template for serviceCatalogItemId {}: {}", serviceCatalogItemId, e.getMessage());
        }
        return List.of();
    }
}
