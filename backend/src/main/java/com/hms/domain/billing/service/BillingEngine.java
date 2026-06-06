package com.hms.domain.billing.service;

import com.hms.domain.billing.event.BillMutatedEvent;
import com.hms.domain.billing.model.*;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * BillingEngine — stateful domain service wrapping a single Bill.
 *
 * This is NOT a Spring bean. It is instantiated per billing operation by
 * BillingEngineFactory and discarded after the transaction commits.
 * All billing arithmetic, status logic, and business rule enforcement live
 * here.
 *
 * Replaces legacy BillBO with corrected naming, long money fields (not int),
 * and explicit business rule exceptions instead of silent failures.
 */
@Getter @Slf4j
public class BillingEngine {

    private final Bill bill;
    private final SequenceNumberPort sequenceNumberPort;
    private final ApplicationEventPublisher eventPublisher;

    private boolean chargeAdded = false;
    private boolean billGenerated = false;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** For creating a new DRAFT bill. */
    public BillingEngine(Bill bill, boolean isDraft,
            SequenceNumberPort sequenceNumberPort,
            ApplicationEventPublisher eventPublisher) {
        this.bill = bill;
        this.sequenceNumberPort = sequenceNumberPort;
        this.eventPublisher = eventPublisher;
        if (isDraft) {
            bill.setBillStatus(BillStatus.DRAFT);
        }
    }

    /** For attaching to an existing bill for mutation. */
    public BillingEngine(Bill bill,
            SequenceNumberPort sequenceNumberPort,
            ApplicationEventPublisher eventPublisher) {
        if (bill.getId() == null) {
            throw new BusinessRuleViolationException("Bill id is required for update");
        }
        this.bill = bill;
        this.sequenceNumberPort = sequenceNumberPort;
        this.eventPublisher = eventPublisher;
    }

    // ── addLineItems ─────────────────────────────────────────────────────────

    /**
     * Adds new charge line items to the bill.
     * Enforces: new items must not carry an existing id (they are truly new).
     */
    /**
     * After addLineItems(): check diagnosticOrderPending flag.
     * BillingOperationsService reads this flag and calls DiagnosticOrderingService
     * if any DIAGNOSTICS-category charge was added (mirrors legacy isChargeAdded
     * flow).
     */
    public void addLineItems(List<ChargeLineItem> items) {
        if (!bill.isDraft()) {
            throw new BusinessRuleViolationException("Items can only be added to DRAFT bills");
        }
        if (bill.getPaymentTotal() > 0 && bill.isOutpatient()) {
            throw new BusinessRuleViolationException("Items cannot be added after payments have been recorded.");
        }
        for (ChargeLineItem item : items) {
            if (item.getId() != null) {
                throw new BusinessRuleViolationException(
                        "Cannot add a charge that already has an id — use updateCharge instead");
            }
            item.setBill(bill);
            bill.getChargeLineItems().add(item);
            bill.addToBillAmount(item.getAmount());
        }
        this.chargeAdded = true;
        publishMutation("ADD_LINE_ITEMS");
    }

    // ── recordPayment ────────────────────────────────────────────────────────

    /**
     * Records a payment (DEPOSIT or PAYMENT) against the bill.
     *
     * Business rules:
     * DEPOSIT — only allowed on DRAFT bills.
     * PAYMENT — only allowed on generated (non-DRAFT) bills.
     */
    public void recordPayment(Payment payment) {
        if (payment.getPaymentType() == PaymentType.DEPOSIT || payment.getPaymentType() == PaymentType.PAYMENT) {
            long due = bill.computeDueAmount();
            if (payment.getAmount() > due) {
                if (!(bill.isInpatient() && payment.isDeposit())) {
                    throw new BusinessRuleViolationException(
                        "Collection amount (" + (payment.getAmount() / 100.0) + ") cannot exceed the due amount (" + (due / 100.0) + ")");
                }
            }
        }

        if (payment.isDeposit() && !bill.isDraft()) {
            throw new BusinessRuleViolationException(
                    "Advance deposit cannot be collected on a generated bill");
        }
        if (payment.isPayment() && bill.isDraft()) {
            throw new BusinessRuleViolationException(
                    "Generate the bill before collecting a payment");
        }

        payment.setBill(bill);
        payment.setRecordedAt(Instant.now());
        if (payment.getPaymentDate() == null) {
            payment.setPaymentDate(LocalDate.now());
        }
        bill.getPayments().add(payment);

        if (payment.isRefund() || payment.isAdvanceRefund()) {
            bill.addToRefundTotal(payment.getAmount());
        } else {
            bill.addToPaymentTotal(payment.getAmount());
        }

        recomputeStatus();
        publishMutation("RECORD_PAYMENT");
    }

    // ── applyDiscount ────────────────────────────────────────────────────────

    /**
     * Applies a discount to the bill.
     *
     * Business rule: totalDiscount MUST exactly equal the sum of per-line
     * discounts.
     * A mismatch here would leave the bill's discountTotal inconsistent with
     * individual line discount_amount values.
     */
    public void applyDiscount(long totalDiscount, List<LineItemDiscount> lineDiscounts) {
        if (!bill.isDraft()) {
            throw new BusinessRuleViolationException("Discounts can only be applied to DRAFT bills");
        }
        if (bill.getPaymentTotal() > 0 && bill.isOutpatient()) {
            throw new BusinessRuleViolationException("Discounts cannot be modified after payments have been recorded");
        }
        long lineSum = lineDiscounts.stream().mapToLong(LineItemDiscount::amount).sum();
        if (totalDiscount != lineSum) {
            throw new BusinessRuleViolationException(
                    "Discount total " + totalDiscount +
                            " does not equal the sum of per-line discounts " + lineSum);
        }
        if (totalDiscount > bill.getBillAmount()) {
            throw new BusinessRuleViolationException(
                    "Total discount cannot exceed bill amount");
        }
        
        // Clear all previous discounts first to avoid desync when partially updating
        bill.getChargeLineItems().forEach(cli -> cli.setDiscountAmount(0L));

        // Apply per-line discount amounts to the actual ChargeLineItem entities
        for (LineItemDiscount ld : lineDiscounts) {
            bill.getChargeLineItems().stream()
                    .filter(cli -> ld.chargeLineItemId().equals(cli.getId()) ||
                                   ld.chargeLineItemId().equals(cli.getDiagnosticOrderLineId()) ||
                                   ld.chargeLineItemId().equals(cli.getPharmacySaleId()))
                    .findFirst()
                    .ifPresent(cli -> {
                        if (ld.amount() > cli.getAmount()) {
                            throw new BusinessRuleViolationException(
                                    "Line discount (" + ld.amount() + ") cannot exceed line amount (" + cli.getAmount() + ") for item " + cli.getId());
                        }
                        cli.setDiscountAmount(ld.amount());
                    });
        }
        bill.setDiscountTotal(totalDiscount);
        recomputeStatus();
        publishMutation("APPLY_DISCOUNT");
    }

    /** Removes all discounts — sets all line discounts and total to 0. */
    public void cancelDiscount() {
        if (!bill.isDraft()) {
            throw new BusinessRuleViolationException("Discounts can only be cancelled on DRAFT bills");
        }
        if (bill.getPaymentTotal() > 0 && bill.isOutpatient()) {
            throw new BusinessRuleViolationException("Discounts cannot be modified after payments have been recorded");
        }
        bill.getChargeLineItems().forEach(cli -> cli.setDiscountAmount(0L));
        bill.setDiscountTotal(0L);
        recomputeStatus();
        publishMutation("CANCEL_DISCOUNT");
    }

    // ── generateBill ─────────────────────────────────────────────────────────

    /**
     * Finalises a DRAFT bill.
     *
     * For INPATIENT bills: dischargeDate must be >= admissionDate.
     * If dueAmount < 0 (overpayment): auto-creates an ADVANCE_REFUND payment.
     */
    public void generateBill(LocalDate billDate, Instant dischargeAt) {
        if (!bill.isDraft()) {
            throw new BusinessRuleViolationException("Bill is already generated");
        }
        if (bill.isInpatient()) {
            if (dischargeAt != null && bill.getAdmissionAt() != null
                    && dischargeAt.isBefore(bill.getAdmissionAt())) {
                throw new BusinessRuleViolationException(
                        "Discharge date cannot be before admission date");
            }
            bill.setDischargeAt(dischargeAt);
        }
        bill.setBillDate(billDate);

        // Auto-refund overpayment
        long due = bill.computeDueAmount();
        if (due < 0) {
            Payment autoRefund = new Payment();
            autoRefund.setBill(bill);
            autoRefund.setAmount(Math.abs(due));
            autoRefund.setPaymentType(PaymentType.ADVANCE_REFUND);
            autoRefund.setPaymentMode(PaymentMode.CASH);
            autoRefund.setPaymentDate(LocalDate.now());
            autoRefund.setNotes("Auto-generated on bill generation — overpayment refund");
            autoRefund.setSequenceNumber(sequenceNumberPort.generateNext(DocumentType.ADVANCE_REFUND));
            bill.getPayments().add(autoRefund);
            bill.addToRefundTotal(autoRefund.getAmount());
        }

        this.billGenerated = true;
        recomputeStatus();
        publishMutation("GENERATE_BILL");
    }

    // ── removeLineItem ───────────────────────────────────────────────────────

    public void removeLineItem(UUID chargeLineItemId, String reason) {
        if (!bill.isDraft()) {
            throw new BusinessRuleViolationException("Items can only be removed from DRAFT bills");
        }
        if (bill.getPaymentTotal() > 0 && bill.isOutpatient()) {
            throw new BusinessRuleViolationException("Items cannot be removed after payments have been recorded. Use Refund instead.");
        }
        ChargeLineItem line = findLineOrThrow(chargeLineItemId);
        if (line.isBedCharge() && line.getQuantity() == 0) {
            // Running bed charge not yet calculated — remove entirely
            bill.getChargeLineItems().remove(line);
        } else {
            line.cancel(reason);
        }
        bill.subtractFromBillAmount(line.getAmount());
        if (line.getDiscountAmount() > 0) {
            bill.setDiscountTotal(bill.getDiscountTotal() - line.getDiscountAmount());
            line.setDiscountAmount(0); // Clear it on the line too
        }
        publishMutation("REMOVE_LINE_ITEM");
    }

    // ── refundLineItems ──────────────────────────────────────────────────────

    public void refundLineItems(List<UUID> lineItemIds, Payment refundPayment) {
        List<UUID> targetIds = lineItemIds;
        if (targetIds == null || targetIds.isEmpty()) {
            targetIds = bill.getChargeLineItems().stream()
                    .filter(ChargeLineItem::isActive)
                    .map(ChargeLineItem::getId)
                    .toList();
        }

        long refundableAmount = 0L;
        long discountReversed = 0L;
        for (UUID id : targetIds) {
            ChargeLineItem line = findLineOrThrow(id);
            line.markRefunded();
            refundableAmount += line.getAmount();
            // Reverse discount on refunded lines
            if (line.getDiscountAmount() > 0) {
                discountReversed += line.getDiscountAmount();
            }
        }
        
        long netRefundable = refundableAmount - discountReversed;
        // Round to the nearest rupee (100 paise) so that fractional proportional
        // discount distributions don't cause spurious validation failures against
        // the rounded amounts the frontend displays and submits.
        long netRefundableRounded = Math.round(netRefundable / 100.0) * 100L;
        if (refundPayment.getAmount() > netRefundableRounded) {
            throw new BusinessRuleViolationException(
                "Refund amount (₹" + Math.round(refundPayment.getAmount() / 100.0) + ") cannot exceed the net charged amount for the selected lines (₹" + Math.round(netRefundable / 100.0) + ")");
        }

        // The service refund amount (the reduction in the bill) must match the cash refund given
        // to keep the due amount balanced at zero.
        bill.addToServiceRefundTotal(refundPayment.getAmount());
        
        // Track the associated discount reversed for auditing/reporting
        if (discountReversed > 0) {
            bill.addToDiscountRefundTotal(discountReversed);
        }
        refundPayment.setPaymentType(PaymentType.REFUND);
        refundPayment.setBill(bill);
        refundPayment.setPaymentDate(LocalDate.now());
        bill.getPayments().add(refundPayment);
        bill.addToRefundTotal(refundPayment.getAmount());
        recomputeStatus();
        publishMutation("REFUND");
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Recomputes BillStatus from current money totals.
     * This is the ONLY place status is set — never via direct setStatus() from
     * callers.
     *
     * Logic:
     * OP bills: REFUNDED status is never set — stays SETTLED (refund amounts
     *           are displayed in the dashboard but don't change status).
     * IP bills: If billAmount - discountTotal == serviceRefundTotal → REFUNDED
     * All:      If dueAmount <= 0 → SETTLED, else → WITH_DUE
     */
    private void recomputeStatus() {
        // A draft bill must remain DRAFT until explicitly generated.
        if (bill.isDraft() && !this.billGenerated) {
            return;
        }

        // Sync bill-level discountTotal from actual line-item discounts to prevent drift.
        // Include REFUNDED items since we want the top-level Discount card to show the total original discount.
        long lineDiscountSum = bill.getChargeLineItems().stream()
                .filter(cli -> cli.getLineStatus() != ChargeLineStatus.CANCELLED)
                .mapToLong(ChargeLineItem::getDiscountAmount)
                .sum();
        if (lineDiscountSum != bill.getDiscountTotal()) {
            log.warn("Bill {} discountTotal drift detected: stored={}, lineSum={}. Syncing.",
                    bill.getId(), bill.getDiscountTotal(), lineDiscountSum);
            bill.setDiscountTotal(lineDiscountSum);
        }

        long netCharged = bill.getBillAmount() - bill.getDiscountTotal();
        long due = bill.computeDueAmount();
        
        log.info("Recomputing status for bill {}: amount={}, discount={}, paid={}, refund={}, serviceRefund={}, netCharged={}, due={}",
            bill.getId(), bill.getBillAmount(), bill.getDiscountTotal(), bill.getPaymentTotal(), bill.getRefundTotal(), bill.getServiceRefundTotal(), netCharged, due);

        BillStatus oldStatus = bill.getBillStatus();
        
        // Check if all non-cancelled charge line items are refunded.
        boolean allRefunded = false;
        long nonCancelledCount = bill.getChargeLineItems().stream()
            .filter(item -> item.getLineStatus() != ChargeLineStatus.CANCELLED)
            .count();
        if (nonCancelledCount > 0) {
            long refundedCount = bill.getChargeLineItems().stream()
                .filter(item -> item.getLineStatus() == ChargeLineStatus.REFUNDED)
                .count();
            if (refundedCount == nonCancelledCount) {
                allRefunded = true;
            }
        }

        if (allRefunded) {
            bill.setBillStatus(BillStatus.CANCELLED);
            if (bill.getCancelledAt() == null) {
                bill.setCancelledAt(Instant.now());
            }
        } else {
            if (bill.getCancelledAt() != null && oldStatus == BillStatus.CANCELLED) {
                bill.setCancelledAt(null);
            }

            // OP bills: never transition to REFUNDED — stay SETTLED
            // IP bills: full net refund → REFUNDED
            if (!bill.isOutpatient() && netCharged == bill.getServiceRefundTotal() && netCharged > 0) {
                bill.setBillStatus(BillStatus.REFUNDED);
            } else if (due <= 0) {
                bill.setBillStatus(BillStatus.SETTLED);
            } else if (bill.getPaymentTotal() > 0 || bill.getDiscountTotal() > 0) {
                bill.setBillStatus(BillStatus.WITH_DUE);
            } else {
                // If not draft anymore, but nothing paid, it's WITH_DUE
                bill.setBillStatus(bill.isDraft() ? BillStatus.DRAFT : BillStatus.WITH_DUE);
            }
        }
        
        if (oldStatus != bill.getBillStatus()) {
            log.info("Bill {} status changed from {} to {}", bill.getId(), oldStatus, bill.getBillStatus());
        }
    }

    private ChargeLineItem findLineOrThrow(UUID id) {
        return bill.getChargeLineItems().stream()
                .filter(cli -> id.equals(cli.getId()) ||
                               id.equals(cli.getDiagnosticOrderLineId()) ||
                               id.equals(cli.getPharmacySaleId()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Charge line item not found on bill: " + id));
    }

    private void publishMutation(String operationType) {
        eventPublisher.publishEvent(new BillMutatedEvent(
                bill.getId(), bill.getBillStatus(), bill.getModifiedBy(), Instant.now(), operationType));
    }

    /**
     * Updates rate and quantity on an existing charge line.
     * Records a BillDetailModified audit before overwriting.
     * Only valid on Draft bills.
     */
    public void updateLineItem(UUID lineItemId, long newRate, int newQty, long discount, String reason) {
        if (!bill.isDraft()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                    "Charges can only be edited on Draft bills");
        }
        if (bill.getPaymentTotal() > 0 && bill.isOutpatient()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                    "Charges cannot be edited after payments have been recorded");
        }
        
        ChargeLineItem line = bill.getChargeLineItems().stream()
                .filter(cli -> lineItemId.equals(cli.getId()) ||
                               lineItemId.equals(cli.getDiagnosticOrderLineId()) ||
                               lineItemId.equals(cli.getPharmacySaleId()))
                .findFirst()
                .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException(
                        "ChargeLineItem", lineItemId));

        if (bill.getPaymentTotal() > 0 && bill.isOutpatient() && discount != line.getDiscountAmount()) {
            throw new com.hms.exception.BusinessRuleViolationException(
                    "Discount cannot be modified after payments have been recorded");
        }

        // Update bill amount delta
        long oldAmount = line.getAmount();
        long newAmount = newRate * newQty;
        bill.addToBillAmount(newAmount - oldAmount);

        // Store old values for audit
        line.setAuditPreviousRate(line.getUnitRate());
        line.setAuditPreviousQty(line.getQuantity());
        line.setAuditPreviousAmt(line.getAmount());
        line.setAuditReason(reason);
        line.setLineStatus(com.hms.domain.billing.model.ChargeLineStatus.MODIFIED);

        // Apply new values
        line.setUnitRate(newRate);
        line.setQuantity(newQty);
        line.setAmount(newAmount);
        
        long oldDiscount = line.getDiscountAmount();
        line.setDiscountAmount(discount);
        bill.setDiscountTotal(bill.getDiscountTotal() + (discount - oldDiscount));
    }

    /** Value record for per-line discount amounts passed to applyDiscount(). */
    public record LineItemDiscount(UUID chargeLineItemId, long amount) {
    }
}
