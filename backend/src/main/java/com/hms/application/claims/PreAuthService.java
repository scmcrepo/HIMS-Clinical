package com.hms.application.claims;

import com.hms.application.compliance.ConsentPurpose;
import com.hms.api.shared.ConsentAttestation;
import com.hms.application.compliance.ConsentGate;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.nhcx.NhcxClient;
import com.hms.infrastructure.persistence.nhcx.NhcxTransactionEntity;
import com.hms.infrastructure.persistence.nhcx.NhcxTransactionJpaRepository;
import com.hms.infrastructure.persistence.preauth.PreAuthEnhancementEntity;
import com.hms.infrastructure.persistence.preauth.PreAuthEstimateLineEntity;
import com.hms.infrastructure.persistence.preauth.PreAuthEnhancementJpaRepository;
import com.hms.infrastructure.persistence.preauth.PreAuthEstimateLineJpaRepository;
import com.hms.infrastructure.persistence.preauth.PreAuthQueryJpaRepository;
import com.hms.infrastructure.persistence.preauth.PreAuthQueryEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cashless pre-authorisation — Module 4, Screens 4.1 to 4.4.
 *
 * <p>All arithmetic is delegated to {@link PreAuthEstimateCalculator}, which is
 * dependency-free and separately tested. Nothing here recomputes money.
 *
 * <p>Pre-auth states live on {@code nhcx_transactions.state}: SUBMITTED,
 * APPROVED, REJECTED, QUERY_RAISED. That is the exchange state — distinct from
 * {@code financial_state}, which only becomes meaningful once a claim is filed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthService {

    public static final String STATE_SUBMITTED = "SUBMITTED";
    public static final String STATE_APPROVED = "APPROVED";
    public static final String STATE_REJECTED = "REJECTED";
    public static final String STATE_QUERY_RAISED = "QUERY_RAISED";

    private final NhcxClient nhcx;
    private final NhcxTransactionJpaRepository transactions;
    private final PreAuthEstimateLineJpaRepository estimateLines;
    private final PreAuthQueryJpaRepository queries;
    private final PreAuthEnhancementJpaRepository enhancements;
    private final ConsentGate consentGate;
    private final MeterRegistry meters;

    /** One estimate line as the form submits it. */
    public record EstimateLineCmd(String category, String description, BigDecimal quantity,
                                  long unitAmountPaise) {
    }

    /**
     * Submit a pre-auth — Screen 4.1.
     *
     * <p>The estimate total is computed from the lines rather than accepted from
     * the client. A total that does not equal the visible lines is the first
     * thing an insurer queries, and trusting a client-supplied figure makes that
     * mismatch possible.
     */
    @Transactional
    public NhcxTransactionEntity submitPreAuth(UUID patientId, UUID encounterId, UUID insuranceId,
                                               String payerCode, String diagnosisCode,
                                               String diagnosisText, String plannedProcedure,
                                               Integer expectedLosDays, String roomType,
                                               List<EstimateLineCmd> lines,
                                               Map<String, Object> bundle,
                                               ConsentAttestation attestation) {

        // WO-022: previously self-granted the consent it then required.
        consentGate.ensure(patientId, ConsentPurpose.INSURANCE_CLAIM, attestation,
                           "claims.preauth.submit");

        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleViolationException(
                "A pre-auth needs at least one estimate line");
        }
        if (diagnosisCode == null || diagnosisCode.isBlank()) {
            // Payers reject undiagnosed pre-auths, and the rejection arrives days
            // later, by which time the patient is already admitted.
            throw new BusinessRuleViolationException("Select an ICD-10 diagnosis");
        }

        List<PreAuthEstimateCalculator.Line> calcLines = lines.stream()
            .map(l -> new PreAuthEstimateCalculator.Line(l.category(), l.quantity(),
                                                         l.unitAmountPaise()))
            .toList();
        long total = PreAuthEstimateCalculator.estimateTotal(calcLines);

        String correlationId = UUID.randomUUID().toString();

        NhcxTransactionEntity txn = new NhcxTransactionEntity();
        txn.setCorrelationId(correlationId);
        txn.setPayerCode(payerCode);
        txn.setExchangeType("PREAUTH");
        txn.setState(STATE_SUBMITTED);
        txn.setPatientId(patientId);
        txn.setEncounterId(encounterId);
        txn.setInsuranceId(insuranceId);
        txn.setDiagnosisCode(diagnosisCode);
        txn.setDiagnosisText(diagnosisText);
        txn.setPlannedProcedure(plannedProcedure);
        txn.setExpectedLosDays(expectedLosDays);
        txn.setRoomType(roomType);
        txn.setEstimatedAmount(total);
        txn.setClaimedAmount(total);

        // Written before the gateway call so a fast callback finds a row.
        NhcxTransactionEntity saved = transactions.save(txn);

        for (EstimateLineCmd l : lines) {
            PreAuthEstimateLineEntity line = new PreAuthEstimateLineEntity();
            line.setNhcxTransactionId(saved.getId());
            line.setCategory(l.category());
            line.setDescription(l.description());
            line.setQuantity(l.quantity());
            line.setUnitAmount(l.unitAmountPaise());
            line.setLineAmount(PreAuthEstimateCalculator.lineAmount(l.quantity(),
                                                                    l.unitAmountPaise()));
            estimateLines.save(line);
        }

        nhcx.submitPreAuth(bundle, payerCode, correlationId);

        counter("submitted").increment();
        log.info("nhcx.preauth.submitted patientId[{}] correlationId[{}] lines[{}]",
                 patientId, correlationId, lines.size());
        return saved;
    }

    /**
     * Record an insurer query — Screen 4.2 / 4.3.
     *
     * <p>Appends a round rather than replacing. The round number is derived from
     * what already exists, so a repeated gateway delivery lands on the same
     * round instead of inventing a new one.
     */
    @Transactional
    public PreAuthQueryEntity recordQuery(String correlationId, String queryCode,
                                          String queryText) {
        NhcxTransactionEntity txn = transactions.findByCorrelationId(correlationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No pre-auth for correlation " + correlationId));

        List<PreAuthQueryEntity> existing =
            queries.findByNhcxTransactionIdOrderByRoundNumberAsc(txn.getId());

        // A duplicate delivery of the same text must not open a new round.
        for (PreAuthQueryEntity q : existing) {
            if (q.getQueryText() != null && q.getQueryText().equals(queryText)) {
                log.info("nhcx.preauth.query.duplicate correlationId[{}] ignored", correlationId);
                return q;
            }
        }

        PreAuthQueryEntity query = new PreAuthQueryEntity();
        query.setNhcxTransactionId(txn.getId());
        query.setRoundNumber(existing.size() + 1);
        query.setQueryCode(queryCode);
        query.setQueryText(queryText);
        query.setRaisedAt(Instant.now());

        txn.setState(STATE_QUERY_RAISED);
        transactions.save(txn);

        counter("query_raised").increment();
        log.info("nhcx.preauth.query.raised correlationId[{}] round[{}]",
                 correlationId, query.getRoundNumber());
        return queries.save(query);
    }

    /**
     * Answer an insurer query — Screen 4.3.
     *
     * <p>Returns the pre-auth to SUBMITTED, because the insurer is now the party
     * being waited on. Leaving it at QUERY_RAISED would keep it in the desk's
     * queue forever and hide genuinely unanswered queries among answered ones.
     */
    @Transactional
    public PreAuthQueryEntity respondToQuery(UUID queryId, String responseText,
                                             String attachmentIds, UUID actor,
                                             Map<String, Object> bundle) {
        PreAuthQueryEntity query = queries.findById(queryId)
            .orElseThrow(() -> new ResourceNotFoundException("Pre-auth query", queryId));

        if (query.getRespondedAt() != null) {
            throw new BusinessRuleViolationException("This query has already been answered");
        }
        if (responseText == null || responseText.isBlank()) {
            throw new BusinessRuleViolationException("Enter a response for the insurer");
        }

        query.setResponseText(responseText);
        query.setResponseAttachments(attachmentIds);
        query.setRespondedBy(actor);
        query.setRespondedAt(Instant.now());
        queries.save(query);

        NhcxTransactionEntity txn = transactions.findById(query.getNhcxTransactionId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "NHCX transaction", query.getNhcxTransactionId()));

        nhcx.submitPreAuth(bundle, txn.getPayerCode(), txn.getCorrelationId());
        txn.setState(STATE_SUBMITTED);
        transactions.save(txn);

        counter("query_answered").increment();
        log.info("nhcx.preauth.query.answered queryId[{}] round[{}]",
                 queryId, query.getRoundNumber());
        return query;
    }

    /**
     * Ask for more than was approved — Screen 4.4.
     *
     * <p>Only valid once something is approved: an enhancement against an
     * unanswered pre-auth is a resubmission, and sending it as an enhancement
     * confuses the payer's own tracking.
     */
    @Transactional
    public PreAuthEnhancementEntity requestEnhancement(UUID transactionId, long revisedEstimate,
                                                       String justification,
                                                       Map<String, Object> bundle) {
        NhcxTransactionEntity txn = transactions.findById(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("NHCX transaction", transactionId));

        if (txn.getApprovedAmount() == null) {
            throw new BusinessRuleViolationException(
                "Nothing has been approved yet — resubmit the pre-auth instead");
        }
        if (justification == null || justification.isBlank()) {
            // Payers reject unexplained enhancements almost by default.
            throw new BusinessRuleViolationException(
                "Explain why more cover is needed");
        }

        long previous = txn.getApprovedAmount();
        // Throws when the revised figure is not an increase.
        long delta = PreAuthEstimateCalculator.enhancementDelta(previous, revisedEstimate);

        List<PreAuthEnhancementEntity> existing =
            enhancements.findByNhcxTransactionIdOrderBySequenceNumberAsc(transactionId);

        String correlationId = UUID.randomUUID().toString();

        PreAuthEnhancementEntity enhancement = new PreAuthEnhancementEntity();
        enhancement.setNhcxTransactionId(transactionId);
        enhancement.setSequenceNumber(existing.size() + 1);
        enhancement.setPreviousApproved(previous);
        enhancement.setRevisedEstimate(revisedEstimate);
        enhancement.setJustification(justification);
        enhancement.setCorrelationId(correlationId);
        enhancement.setEnhancementState("SUBMITTED");

        PreAuthEnhancementEntity saved = enhancements.save(enhancement);

        nhcx.submitPreAuth(bundle, txn.getPayerCode(), correlationId);

        counter("enhancement_requested").increment();
        log.info("nhcx.preauth.enhancement.requested txnId[{}] seq[{}] deltaPaise[{}]",
                 transactionId, saved.getSequenceNumber(), delta);
        return saved;
    }

    /** Record the insurer's answer to an enhancement. */
    @Transactional
    public PreAuthEnhancementEntity recordEnhancementOutcome(String correlationId, String state,
                                                             Long approvedAmount) {
        PreAuthEnhancementEntity enhancement = enhancements.findByCorrelationId(correlationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No enhancement for correlation " + correlationId));

        enhancement.setEnhancementState(state);
        enhancement.setApprovedAmount(approvedAmount);
        enhancement.setRespondedAt(Instant.now());
        enhancements.save(enhancement);

        // The headline approved amount moves only on approval; previousApproved
        // on the row keeps the history reconstructable.
        if ("APPROVED".equals(state) && approvedAmount != null) {
            transactions.findById(enhancement.getNhcxTransactionId()).ifPresent(txn -> {
                txn.setApprovedAmount(approvedAmount);
                transactions.save(txn);
            });
        }

        counter("enhancement_" + state.toLowerCase()).increment();
        return enhancement;
    }

    public List<PreAuthEstimateLineEntity> estimateFor(UUID transactionId) {
        return estimateLines.findByNhcxTransactionId(transactionId);
    }

    public List<PreAuthQueryEntity> queriesFor(UUID transactionId) {
        return queries.findByNhcxTransactionIdOrderByRoundNumberAsc(transactionId);
    }

    public List<PreAuthQueryEntity> unansweredQueries() {
        return queries.findByRespondedAtIsNullOrderByRaisedAtAsc();
    }

    public List<PreAuthEnhancementEntity> enhancementsFor(UUID transactionId) {
        return enhancements.findByNhcxTransactionIdOrderBySequenceNumberAsc(transactionId);
    }

    private Counter counter(String event) {
        return Counter.builder("hms.nhcx.preauth.events").tag("event", event).register(meters);
    }
}
