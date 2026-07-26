package com.hms.application.hitl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.hitl.request.OperatorDecisionRequest;
import com.hms.api.hitl.request.RaiseEscalationRequest;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.hitl.HitlEscalationEntity;
import com.hms.infrastructure.persistence.hitl.HitlEscalationJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The human-in-the-loop queue.
 *
 * <p>Every escalation carries a deadline. A graph paused for a human is a
 * patient waiting, and an escalation nobody picks up is worse than an agent that
 * simply declined — the patient believes they are being helped while nothing is
 * happening. {@link #expireOverdue()} is what makes the deadline real.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HitlService {

    /** Default deadline. Deliberately short: this is a person waiting. */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);

    /** Distress cannot sit in a queue for half an hour. */
    private static final Duration DISTRESS_TIMEOUT = Duration.ofMinutes(5);

    private static final Set<String> ACTIONS_REQUIRING_REASON = Set.of("CORRECT", "OVERRIDE");
    private static final Set<String> VALID_ACTIONS =
        Set.of("APPROVE", "CORRECT", "OVERRIDE", "TAKE_OVER");

    private final HitlEscalationJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Transactional
    public HitlEscalationEntity raise(RaiseEscalationRequest req) {
        // A graph that interrupts twice for the same run should update the
        // existing item, not queue a duplicate in front of the operator.
        HitlEscalationEntity entity = repository
            .findByRunIdAndState(req.runId(), "WAITING")
            .orElseGet(HitlEscalationEntity::new);

        entity.setRunId(req.runId());
        entity.setCorrelationId(req.correlationId());
        entity.setChannel(req.channel());
        entity.setReason(req.reason());
        entity.setDetail(truncate(req.detail(), 500));
        entity.setIntent(req.intent());
        entity.setConfidence(req.confidence() == null ? null
            : BigDecimal.valueOf(req.confidence()).setScale(3, java.math.RoundingMode.HALF_UP));
        entity.setProposedActions(req.proposedActions() == null ? List.of() : req.proposedActions());
        entity.setTranscript(serialise(req.transcript()));
        entity.setState("WAITING");

        Duration timeout = req.timeoutSeconds() != null
            ? Duration.ofSeconds(req.timeoutSeconds())
            : ("distress".equalsIgnoreCase(req.reason()) ? DISTRESS_TIMEOUT : DEFAULT_TIMEOUT);
        entity.setExpiresAt(Instant.now().plus(timeout));

        HitlEscalationEntity saved = repository.save(entity);

        meterRegistry.counter("hms_agent_hitl_escalations_total",
                              "reason", req.reason()).increment();
        // Reason and ids only. The transcript is PHI and never reaches a log.
        log.info("agent.hitl.raised runId[{}] reason[{}] channel[{}] expiresAt[{}]",
                 req.runId(), req.reason(), req.channel(), saved.getExpiresAt());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<HitlEscalationEntity> queue() {
        return repository.findByStateOrderByCreatedAtAsc("WAITING");
    }

    @Transactional(readOnly = true)
    public HitlEscalationEntity get(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Escalation not found: " + id));
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> transcriptOf(HitlEscalationEntity entity) {
        if (entity.getTranscript() == null || entity.getTranscript().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(entity.getTranscript(), List.class);
        } catch (Exception e) {
            log.warn("agent.hitl.transcript.unreadable id[{}]", entity.getId());
            return List.of();
        }
    }

    @Transactional
    public HitlEscalationEntity resolve(UUID id, OperatorDecisionRequest decision, UUID operatorId) {
        HitlEscalationEntity entity = get(id);

        String action = decision.action() == null ? "" : decision.action().toUpperCase(java.util.Locale.ROOT);
        if (!VALID_ACTIONS.contains(action)) {
            throw new BusinessRuleViolationException("Unknown operator action: " + decision.action());
        }
        if (!entity.isWaiting()) {
            // Two operators opening the same item is normal; both resolving it
            // is not. Fail the second rather than silently overwriting the first.
            throw new BusinessRuleViolationException(
                "This escalation is already " + entity.getState().toLowerCase(java.util.Locale.ROOT));
        }
        if (ACTIONS_REQUIRING_REASON.contains(action)
            && (decision.reason() == null || decision.reason().isBlank())) {
            throw new BusinessRuleViolationException(
                "A reason is required when correcting or overriding the agent");
        }

        entity.setState("RESOLVED");
        entity.setOperatorAction(action);
        entity.setOperatorReason(truncate(decision.reason(), 500));
        entity.setOperatorReply(decision.reply());
        entity.setResolvedAt(Instant.now());
        entity.setResolvedBy(operatorId);

        HitlEscalationEntity saved = repository.save(entity);

        meterRegistry.counter("hms_agent_hitl_resolutions_total", "action", action).increment();
        long seconds = Duration.between(entity.getCreatedAt(), saved.getResolvedAt()).getSeconds();
        meterRegistry.timer("hms_agent_hitl_resolution_seconds")
            .record(Duration.ofSeconds(Math.max(seconds, 0)));
        log.info("agent.hitl.resolved runId[{}] action[{}] operator[{}] waitedSeconds[{}]",
                 entity.getRunId(), action, operatorId, seconds);
        return saved;
    }

    /**
     * Close out escalations nobody picked up.
     *
     * <p>Runs on a scheduled thread with no tenant context, so the repository
     * query is deliberately tenant-agnostic — the sweep must cover every tenant.
     * Marking them TIMED_OUT is what lets the agent tell the patient a person
     * will call back, instead of leaving them waiting on silence.
     */
    @Scheduled(fixedDelayString = "PT1M")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOverdue() {
        try {
            List<HitlEscalationEntity> overdue = repository.findOverdue(Instant.now());
            for (HitlEscalationEntity e : overdue) {
                e.setState("TIMED_OUT");
                repository.save(e);
                meterRegistry.counter("hms_agent_hitl_timeouts_total",
                                      "reason", e.getReason()).increment();
                log.error("agent.hitl.timed_out runId[{}] reason[{}] waitedSeconds[{}] "
                          + "- nobody picked this up",
                          e.getRunId(), e.getReason(),
                          Duration.between(e.getCreatedAt(), Instant.now()).getSeconds());
            }
        } catch (RuntimeException ex) {
            log.error("agent.hitl.expiry.failed", ex);
        }
    }

    /** Gauge source: how many people are currently waiting on a human. */
    @Transactional(readOnly = true)
    public long waitingCount() {
        return repository.countByState("WAITING");
    }

    private String serialise(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
