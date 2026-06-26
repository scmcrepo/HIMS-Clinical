package com.hms.application.sales;
import com.hms.api.sales.request.CreateSaleRequest;
import com.hms.api.sales.response.PharmacySaleResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.billing.model.Bill;
import com.hms.domain.billing.model.ChargeLineItem;
import com.hms.domain.billing.service.BillingEngine;
import com.hms.infrastructure.persistence.billing.BillJpaRepository;
import com.hms.application.billing.BillingOperationsService;
import com.hms.infrastructure.persistence.encounter.ClinicalEncounterJpaRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.hms.domain.inventory.model.InventoryBatch;
import com.hms.domain.sales.model.*;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryItemJpaRepository;
import com.hms.infrastructure.persistence.sales.PharmacySaleJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class PharmacySaleService {
    private final PharmacySaleJpaRepository saleRepo;
    private final InventoryBatchJpaRepository batchRepo;
    private final PatientJpaRepository patientRepo;
    private final SequenceNumberPort sequencePort;
    private final InventoryItemJpaRepository itemRepo;
    private final com.hms.infrastructure.sequence.NumberSequenceJpaRepository numberSequenceRepo;
    private final BillJpaRepository billRepo;
    private final ClinicalEncounterJpaRepository encounterRepo;
    private final BillingOperationsService billingService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PharmacySaleResponse createSale(CreateSaleRequest req) {
        PharmacySale sale;
        if (req.id() != null) {
            sale = saleRepo.findById(req.id())
                .orElseThrow(() -> new ResourceNotFoundException("PharmacySale", req.id()));
            if (!sale.isDraft()) {
                throw new BusinessRuleViolationException("Cannot edit a finalized sale");
            }
            sale.getLines().clear();
        } else {
            sale = new PharmacySale();
        }
        sale.setPatientId(req.patientId());
        sale.setCustomerName(req.customerName());
        sale.setCustomerPhone(req.customerPhone());
        sale.setConsultantName(req.consultantName());
        sale.setEncounterId(req.encounterId());
        sale.setPrescribedAt(req.prescribedAt());
        sale.setDepartmentId(req.departmentId());
        sale.setSaleDate(LocalDate.now());
        sale.setPaymentMode(req.paymentMode());
        sale.setCardType(req.cardType());
        sale.setCardNumber(req.cardNumber());
        sale.setBankName(req.bankName());

        for (var line : req.lines()) {
            InventoryBatch batch = batchRepo.findByIdForUpdate(line.inventoryBatchId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryBatch", line.inventoryBatchId()));
            if (batch.isExpired()) throw new BusinessRuleViolationException("Batch is expired: " + batch.getBatchNumber());
            if (!req.isDraft()) {
                // Only decrement stock when finalising, not on draft save
                batch.decrementStock(line.quantity());
                batchRepo.save(batch);
            }
            PharmacySaleLine saleLine = new PharmacySaleLine();
            saleLine.setInventoryBatchId(line.inventoryBatchId());
            saleLine.setQuantity(line.quantity());
            saleLine.setUnitRate(line.unitRate().setScale(4, java.math.RoundingMode.HALF_UP));
            
            // MRP is tax-exclusive; calculate tax on top
            var item = itemRepo.findById(batch.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", batch.getItemId()));
            BigDecimal taxRate = item.getTaxRate() != null ? item.getTaxRate() : BigDecimal.ZERO;
            
            BigDecimal lineAmountExclTax = saleLine.getUnitRate().multiply(BigDecimal.valueOf(line.quantity()));
            BigDecimal taxMultiplier = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), java.math.MathContext.DECIMAL128));
            BigDecimal lineAmountInclTax = lineAmountExclTax.multiply(taxMultiplier);
            
            saleLine.setAmount(lineAmountInclTax.setScale(2, java.math.RoundingMode.HALF_UP));
            sale.addLine(saleLine);
        }

        sale.setDiscountAmount(req.discountAmount() != null ? req.discountAmount().setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO);
        sale.recalculate(); // this sets totalAmount

        // If paidAmount is provided, use it. Otherwise assume fully paid for finalized sale, or 0 for draft
        if (req.paidAmount() != null) {
            BigDecimal scalePaidAmount = req.paidAmount().setScale(2, java.math.RoundingMode.HALF_UP);
            if (scalePaidAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleViolationException("Paid amount cannot be negative");
            }
            if (scalePaidAmount.compareTo(sale.getTotalAmount()) > 0) {
                throw new BusinessRuleViolationException("Paid amount cannot exceed the net amount of ₹" + sale.getTotalAmount());
            }
            sale.setPaidAmount(scalePaidAmount);
        } else if (!req.isDraft()) {
            if ("Add to Bill".equalsIgnoreCase(sale.getPaymentMode())) {
                sale.setPaidAmount(BigDecimal.ZERO);
            } else {
                sale.setPaidAmount(sale.getTotalAmount());
            }
        } else {
            sale.setPaidAmount(BigDecimal.ZERO);
        }
        sale.recalculate(); // recalculate again to set dueAmount based on paidAmount

        if (!req.isDraft()) {
            sale.finalise(sequencePort.generateNext(DocumentType.PHARMACY_SALE));
            if (sale.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                PharmacySalePayment payment = new PharmacySalePayment();
                payment.setSale(sale);
                payment.setAmount(sale.getPaidAmount());
                payment.setPaymentMode(sale.getPaymentMode());
                payment.setCardType(sale.getCardType());
                payment.setCardNumber(sale.getCardNumber());
                payment.setBankName(sale.getBankName());
                sale.addPayment(payment);
            }
        } else {
            if (sale.getSequenceNumber() == null || !sale.getSequenceNumber().startsWith("DF-")) {
                int nextVal = saleRepo.findDraftSequenceNumbers().stream()
                    .map(seq -> {
                        try {
                            return Integer.parseInt(seq.replace("DF-", ""));
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .max(Integer::compareTo)
                    .orElse(0) + 1;
                sale.setSequenceNumber(String.format("DF-%04d", nextVal));
            }
        }

        PharmacySale saved = saleRepo.save(sale);

        if (!req.isDraft() && "Add to Bill".equalsIgnoreCase(saved.getPaymentMode())) {
            UUID encounterId = saved.getEncounterId();
            if (encounterId == null && saved.getPatientId() != null) {
                var encounters = encounterRepo.findActiveInpatientByPatientId(saved.getPatientId());
                if (!encounters.isEmpty()) {
                    encounterId = encounters.get(0).getId();
                }
            }

            UUID providerId = null;
            if (encounterId != null) {
                providerId = encounterRepo.findById(encounterId)
                    .map(com.hms.domain.encounter.model.ClinicalEncounter::getPrimaryProviderId)
                    .orElse(null);
            }

            if (encounterId != null) {
                var billResp = billingService.ensureDraftBill(
                    saved.getPatientId(),
                    encounterId,
                    com.hms.domain.billing.model.EncounterType.INPATIENT,
                    providerId
                );

                if (billResp != null) {
                    Bill bill = billRepo.findByIdForUpdate(billResp.id())
                        .orElseThrow(() -> new ResourceNotFoundException("Bill", billResp.id()));

                    // Single consolidated charge line: "PHARMACY SALES ( SL-XXXX )"
                    String chargeName = "PHARMACY SALES ( " + saved.getSequenceNumber() + " )";
                    long totalAmountPaise = saved.getTotalAmount()
                        .multiply(java.math.BigDecimal.valueOf(100))
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                        .longValue();

                    ChargeLineItem pharmacyCharge = new ChargeLineItem();
                    pharmacyCharge.setItemName(chargeName);
                    pharmacyCharge.setUnitRate(totalAmountPaise);
                    pharmacyCharge.setQuantity(1);
                    pharmacyCharge.setAmount(totalAmountPaise);
                    pharmacyCharge.setPharmacySaleId(saved.getId());

                    List<ChargeLineItem> chargeLines = new ArrayList<>();
                    chargeLines.add(pharmacyCharge);

                    BillingEngine engine = new BillingEngine(bill, sequencePort, eventPublisher);
                    engine.addLineItems(chargeLines);
                    billRepo.save(bill);
                }
            }
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PharmacySaleResponse> getByPatient(UUID patientId) {
        return saleRepo.findByPatientId(patientId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PharmacySaleResponse> getByDate(LocalDate date) {
        return saleRepo.findBySaleDate(date).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PharmacySaleResponse> getByDateAndQuery(LocalDate date, String q) {
        List<PharmacySaleResponse> list = getByDate(date);
        if (q == null || q.trim().isEmpty()) {
            return list;
        }
        String lowerQ = q.toLowerCase().trim();
        return list.stream()
            .filter(s -> {
                boolean seqMatch = s.sequenceNumber() != null && s.sequenceNumber().toLowerCase().contains(lowerQ);
                boolean patientNameMatch = s.patientName() != null && s.patientName().toLowerCase().contains(lowerQ);
                boolean customerNameMatch = s.customerName() != null && s.customerName().toLowerCase().contains(lowerQ);
                boolean phoneMatch = s.customerPhone() != null && s.customerPhone().toLowerCase().contains(lowerQ);
                boolean patientNoMatch = s.patientNumber() != null && s.patientNumber().toLowerCase().contains(lowerQ);
                return seqMatch || patientNameMatch || customerNameMatch || phoneMatch || patientNoMatch;
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PharmacySaleResponse> getDraftsByDepartment(UUID departmentId) {
        return saleRepo.findDraftByDepartment(departmentId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PharmacySaleResponse getById(UUID saleId) {
        return toResponse(saleRepo.findById(saleId)
            .orElseThrow(() -> new ResourceNotFoundException("PharmacySale", saleId)));
    }

    @Transactional
    public void deleteSale(UUID saleId) {
        PharmacySale sale = saleRepo.findById(saleId)
            .orElseThrow(() -> new ResourceNotFoundException("PharmacySale", saleId));
        if (!sale.isDraft()) {
            throw new BusinessRuleViolationException("Cannot delete a finalized sale");
        }
        saleRepo.delete(sale);
    }

    @Transactional
    public PharmacySaleResponse collectPayment(UUID saleId, BigDecimal amount, String paymentMode, String bankName, String cardType, String cardNumber) {
        PharmacySale sale = saleRepo.findById(saleId)
            .orElseThrow(() -> new ResourceNotFoundException("PharmacySale", saleId));
        
        if (sale.isDraft()) {
            throw new BusinessRuleViolationException("Cannot collect payment for a draft sale");
        }
        
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Payment amount must be greater than zero");
        }
        
        BigDecimal newPaidAmount = sale.getPaidAmount().add(amount);
        if (newPaidAmount.compareTo(sale.getTotalAmount()) > 0) {
            throw new BusinessRuleViolationException("Total paid amount cannot exceed the sale total amount of " + sale.getTotalAmount());
        }
        
        sale.setPaidAmount(newPaidAmount);
        sale.recalculate();
        
        if (paymentMode != null) sale.setPaymentMode(paymentMode);
        if (bankName != null) sale.setBankName(bankName);
        if (cardType != null) sale.setCardType(cardType);
        if (cardNumber != null) sale.setCardNumber(cardNumber);
        
        if (sale.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            sale.setSaleStatus(SaleStatus.SETTLED);
        } else {
            sale.setSaleStatus(SaleStatus.WITH_DUE);
        }
        
        PharmacySalePayment payment = new PharmacySalePayment();
        payment.setSale(sale);
        payment.setAmount(amount);
        payment.setPaymentMode(paymentMode);
        payment.setCardType(cardType);
        payment.setCardNumber(cardNumber);
        payment.setBankName(bankName);
        sale.addPayment(payment);

        PharmacySale saved = saleRepo.save(sale);
        return toResponse(saved);
    }
    private PharmacySaleResponse toResponse(PharmacySale s) {
        List<PharmacySaleResponse.SaleLineResponse> lineResponses = s.getLines().stream()
            .map(l -> {
                String itemName = "Unknown Item";
                try {
                    var batchOpt = batchRepo.findById(l.getInventoryBatchId());
                    if (batchOpt.isPresent()) {
                        var itemOpt = itemRepo.findById(batchOpt.get().getItemId());
                        if (itemOpt.isPresent()) {
                            itemName = itemOpt.get().getName();
                        }
                    }
                } catch (Exception ignored) {}
                return new PharmacySaleResponse.SaleLineResponse(
                    l.getId(),
                    l.getInventoryBatchId(),
                    itemName,
                    l.getQuantity(),
                    l.getUnitRate(),
                    l.getAmount(),
                    l.getDiscountAmount()
                );
            })
            .collect(Collectors.toList());
        
        List<PharmacySaleResponse.PaymentResponse> paymentResponses = (s.getPayments() != null ? s.getPayments() : Collections.<PharmacySalePayment>emptyList()).stream()
            .map(p -> new PharmacySaleResponse.PaymentResponse(
                p.getId(),
                p.getAmount(),
                p.getPaymentMode(),
                p.getCardType(),
                p.getCardNumber(),
                p.getBankName(),
                p.getCreatedAt()
            ))
            .collect(Collectors.toList());
        
        String patientName = s.getCustomerName() != null ? s.getCustomerName() : "Walk-in";
        String patientNumber = null;
        if (s.getPatientId() != null) {
            patientName = patientRepo.findById(s.getPatientId())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Unknown Patient");
            patientNumber = numberSequenceRepo.findById(s.getPatientId())
                .map(com.hms.infrastructure.sequence.NumberSequenceEntity::getValue)
                .orElse(null);
        }

        String customerType = "Walk-in";
        if (s.getPatientId() != null) {
            if (s.getEncounterId() != null) {
                var encOpt = encounterRepo.findById(s.getEncounterId());
                if (encOpt.isPresent()) {
                    customerType = encOpt.get().getEncounterType() == com.hms.domain.billing.model.EncounterType.INPATIENT ? "IP" : "OP";
                } else {
                    customerType = "OP";
                }
            } else {
                var activeIp = encounterRepo.findActiveInpatientByPatientId(s.getPatientId());
                if (activeIp != null && !activeIp.isEmpty()) {
                    customerType = "IP";
                } else {
                    customerType = "OP";
                }
            }
        }

        String sequenceNumber = s.getSequenceNumber();
        if (sequenceNumber == null && s.isDraft()) {
            sequenceNumber = "DF-" + s.getId().toString().substring(0, 8).toUpperCase();
        }

        return new PharmacySaleResponse(s.getId(), s.getPatientId(), patientName, 
            s.getCustomerName(), s.getCustomerPhone(), s.getConsultantName(),
            s.getEncounterId(), s.getDepartmentId(), s.getPrescribedAt(),
            sequenceNumber, s.getSaleDate(), s.getTotalAmount(), s.getDiscountAmount(), s.getSaleStatus(), lineResponses,
            s.getCreatedAt() != null ? s.getCreatedAt() : java.time.Instant.now(),
            s.getPaymentMode(), s.getCardType(), s.getCardNumber(), s.getBankName(),
            s.getPaidAmount(), s.getDueAmount(), paymentResponses, patientNumber, customerType);
    }
}
