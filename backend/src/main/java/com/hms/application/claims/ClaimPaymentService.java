package com.hms.application.claims;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.nhcx.NhcxTransactionEntity;
import com.hms.infrastructure.persistence.nhcx.NhcxTransactionJpaRepository;
import com.hms.infrastructure.persistence.payment.ClaimDeductionLineEntity;
import com.hms.infrastructure.persistence.payment.ClaimDeductionLineJpaRepository;
import com.hms.infrastructure.persistence.payment.ClaimPaymentAdviceEntity;
import com.hms.infrastructure.persistence.payment.ClaimPaymentAdviceJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Insurer disbursal tracking and bank reconciliation — Screens 5.2 and 5.3.
 *
 * <p>Closes the loop the flow document describes: claim approved, insurer
 * initiates a transfer and issues a PaymentNotice carrying a UTR, and the
 * hospital's accounts team confirms the credit actually landed. Until that last
 * step a claim is approved but unpaid, which is the state most likely to be
 * lost track of and the reason the control tower exists.
 *
 * <p>All arithmetic is delegated to {@link ClaimSettlementCalculator}, which is
 * dependency-free and separately tested. Nothing in this class recomputes money.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimPaymentService {

    private final ClaimPaymentAdviceJpaRepository advices;
    private final ClaimDeductionLineJpaRepository deductions;
    private final NhcxTransactionJpaRepository transactions;
    private final MeterRegistry meters;

    /**
     * Record a PaymentNotice from the payer.
     *
     * <p><b>Idempotent on UTR.</b> Gateways deliver at least once, and a
     * duplicate advice that credited the ledger twice would overstate receipts
     * by the value of the payment. The unique index backs this up at the
     * database level, so a concurrent duplicate fails loudly rather than
     * slipping past the check.
     */
    @Transactional
    public ClaimPaymentAdviceEntity recordPaymentAdvice(String correlationId, JsonNode notice) {
        NhcxTransactionEntity txn = transactions.findByCorrelationId(correlationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No claim found for correlation " + correlationId));

        String utr = text(notice, "identifier");
        if (utr == null || utr.isBlank()) {
            // Without a UTR the advice cannot be matched to a bank line, which
            // makes it unreconcilable and therefore useless.
            throw new BusinessRuleViolationException(
                "Payment notice carries no UTR / transaction reference");
        }

        var existing = advices.findByUtrNumber(utr);
        if (existing.isPresent()) {
            log.info("nhcx.payment.duplicate utr[{}] correlationId[{}] ignored", utr, correlationId);
            meters.counter("hms_nhcx_payment_advices_total", "outcome", "duplicate").increment();
            return existing.get();
        }

        long gross = paise(notice.path("amount").path("value"));
        long tds = paise(notice.path("tdsAmount").path("value"));
        long deduction = paise(notice.path("deductionAmount").path("value"));
        long net = notice.path("netAmount").path("value").isMissingNode()
            ? ClaimSettlementCalculator.netPayable(gross, tds, deduction)
            : paise(notice.path("netAmount").path("value"));

        ClaimPaymentAdviceEntity advice = new ClaimPaymentAdviceEntity();
        advice.setNhcxTransactionId(txn.getId());
        advice.setCorrelationId(correlationId);
        advice.setPayerCode(txn.getPayerCode());
        advice.setUtrNumber(utr);
        advice.setPaymentDate(instant(notice, "created"));
        advice.setGrossAmount(gross);
        advice.setTdsAmount(tds);
        advice.setDeductionAmount(deduction);
        advice.setNetDisbursedAmount(net);
        advice.setRawPayload(notice.toString());

        ClaimPaymentAdviceEntity saved = advices.save(advice);

        txn.setFinancialState(ClaimSettlementCalculator.PAYMENT_INITIATED);
        transactions.save(txn);

        meters.counter("hms_nhcx_payment_advices_total", "outcome", "recorded").increment();
        // UTR is a bank reference, not personal data, and is the key an auditor
        // traces. Amounts stay out of the log line.
        log.info("nhcx.payment.advice.recorded correlationId[{}] utr[{}] adviceId[{}]",
                 correlationId, utr, saved.getId());
        return saved;
    }

    /**
     * Confirm the hospital's bank actually received the money — Screen 5.3.
     *
     * <p>A mismatch between the advice and the bank credit is recorded rather
     * than rejected: the money did arrive, just not the advised amount, and
     * refusing to reconcile would leave the claim looking unpaid. The gap is
     * surfaced and the claim moves to CLAIM_DISPUTED so someone chases it.
     */
    @Transactional
    public ClaimPaymentAdviceEntity reconcile(UUID adviceId, long bankCreditedPaise,
                                              UUID actor, String note) {
        ClaimPaymentAdviceEntity advice = advices.findById(adviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment advice", adviceId));

        if (advice.isReconciled()) {
            throw new BusinessRuleViolationException("This payment advice is already reconciled");
        }
        if (bankCreditedPaise < 0) {
            throw new BusinessRuleViolationException("Bank credited amount cannot be negative");
        }

        advice.setBankCreditedAmount(bankCreditedPaise);
        advice.setReconciled(true);
        advice.setReconciledAt(Instant.now());
        advice.setReconciledBy(actor);
        advice.setReconciliationNote(note);
        advices.save(advice);

        long gap = ClaimSettlementCalculator.reconciliationGap(
            advice.getNetDisbursedAmount(), bankCreditedPaise);
        boolean matched = gap == 0L;

        NhcxTransactionEntity txn = transactions.findById(advice.getNhcxTransactionId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "NHCX transaction", advice.getNhcxTransactionId()));

        txn.setFinancialState(matched
            ? ClaimSettlementCalculator.AMOUNT_RECEIVED_IN_BANK
            : ClaimSettlementCalculator.CLAIM_DISPUTED);
        transactions.save(txn);

        meters.counter("hms_nhcx_reconciliations_total",
                       "outcome", matched ? "matched" : "mismatched").increment();
        log.info("nhcx.payment.reconciled adviceId[{}] utr[{}] matched[{}] gapPaise[{}]",
                 adviceId, advice.getUtrNumber(), matched, gap);
        return advice;
    }

    /** Record what the insurer disallowed, itemised so billing can dispute a line. */
    @Transactional
    public List<ClaimDeductionLineEntity> recordDeductions(
            UUID nhcxTransactionId, List<DeductionLine> lines) {

        List<ClaimDeductionLineEntity> saved = lines.stream().map(l -> {
            ClaimDeductionLineEntity e = new ClaimDeductionLineEntity();
            e.setNhcxTransactionId(nhcxTransactionId);
            e.setReasonCategory(l.category());
            e.setReasonCode(l.code());
            e.setDescription(l.description());
            e.setAmount(l.amountPaise());
            return deductions.save(e);
        }).toList();

        long total = saved.stream().mapToLong(ClaimDeductionLineEntity::getAmount).sum();
        transactions.findById(nhcxTransactionId).ifPresent(txn -> {
            txn.setDisallowedAmount(total);
            transactions.save(txn);
        });

        log.info("nhcx.claim.deductions.recorded txnId[{}] lines[{}]",
                 nhcxTransactionId, saved.size());
        return saved;
    }

    /** Challenge a specific disallowed line — Screen 5.2's dispute action. */
    @Transactional
    public ClaimDeductionLineEntity disputeLine(UUID lineId, String note) {
        ClaimDeductionLineEntity line = deductions.findById(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("Deduction line", lineId));

        line.setDisputed(true);
        line.setDisputedAt(Instant.now());
        line.setDisputeNote(note);
        deductions.save(line);

        transactions.findById(line.getNhcxTransactionId()).ifPresent(txn -> {
            txn.setFinancialState(ClaimSettlementCalculator.CLAIM_DISPUTED);
            transactions.save(txn);
        });

        counter("disputed").increment();
        log.info("nhcx.claim.line.disputed lineId[{}] txnId[{}]",
                 lineId, line.getNhcxTransactionId());
        return line;
    }

    public List<ClaimPaymentAdviceEntity> advicesFor(UUID nhcxTransactionId) {
        return advices.findByNhcxTransactionId(nhcxTransactionId);
    }

    public List<ClaimPaymentAdviceEntity> pendingReconciliation() {
        return advices.findByReconciledFalseOrderByPaymentDateAsc();
    }

    public List<ClaimDeductionLineEntity> deductionsFor(UUID nhcxTransactionId) {
        return deductions.findByNhcxTransactionId(nhcxTransactionId);
    }

    private Counter counter(String event) {
        return Counter.builder("hms.nhcx.claim.events").tag("event", event).register(meters);
    }

    /** Rupees to paise via BigDecimal — never a double cast. */
    static long paise(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return 0L;
        }
        try {
            return new BigDecimal(value.asText())
                .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            log.warn("nhcx.payment.amount.unparseable type[{}]", e.getClass().getSimpleName());
            return 0L;
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static Instant instant(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null) return null;
        try {
            return Instant.parse(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** One disallowed item as the payer described it. */
    public record DeductionLine(String category, String code, String description,
                                long amountPaise) {
    }
}
