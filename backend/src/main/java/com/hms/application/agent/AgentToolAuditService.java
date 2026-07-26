package com.hms.application.agent;

import com.hms.infrastructure.observability.CorrelationIdFilter;
import com.hms.infrastructure.persistence.agent.AgentToolInvocationEntity;
import com.hms.infrastructure.persistence.agent.AgentToolInvocationJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Writes one append-only audit row per agent tool call, and the matching metrics.
 *
 * <p>Runs in {@code REQUIRES_NEW} so an audit write survives a rollback of the
 * business transaction. An action that failed and left no trace is exactly the
 * one an investigation later needs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolAuditService {

    private final AgentToolInvocationJpaRepository repository;
    private final MeterRegistry meterRegistry;

    /**
     * Run a tool, recording the outcome either way.
     *
     * @param targetIdExtractor pulls a surrogate id out of the result for the
     *                          audit row. Must return an id, never patient data.
     */
    public <T> T record(String toolName, UUID tokenId, String idempotencyKey,
                        Supplier<T> action, java.util.function.Function<T, UUID> targetIdExtractor) {
        long startNanos = System.nanoTime();
        String outcome = "SUCCESS";
        String errorCode = null;
        T result = null;
        try {
            result = action.get();
            return result;
        } catch (RuntimeException ex) {
            outcome = "FAILURE";
            errorCode = ex.getClass().getSimpleName();
            throw ex;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            UUID targetId = null;
            if (result != null && targetIdExtractor != null) {
                try {
                    targetId = targetIdExtractor.apply(result);
                } catch (RuntimeException ignored) {
                    // Never let audit enrichment break the call.
                }
            }
            persist(toolName, tokenId, idempotencyKey, outcome, errorCode, durationMs, targetId);
            meterRegistry.counter("hms_agent_tool_invocations_total",
                                  "tool", toolName, "outcome", outcome.toLowerCase()).increment();
            Timer.builder("hms_agent_tool_duration_seconds")
                .tag("tool", toolName)
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(durationMs));
            log.info("agent.tool.completed tool[{}] outcome[{}] durationMs[{}]",
                     toolName, outcome, durationMs);
        }
    }

    public <T> T record(String toolName, UUID tokenId, Supplier<T> action) {
        return record(toolName, tokenId, null, action, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persist(String toolName, UUID tokenId, String idempotencyKey,
                           String outcome, String errorCode, long durationMs, UUID targetId) {
        try {
            AgentToolInvocationEntity row = new AgentToolInvocationEntity();
            row.setToolName(toolName);
            row.setTokenId(tokenId);
            row.setIdempotencyKey(idempotencyKey);
            row.setOutcome(outcome);
            row.setErrorCode(errorCode);
            row.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
            row.setTargetEntityId(targetId);
            row.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID));
            row.setRunId(MDC.get(CorrelationIdFilter.MDC_RUN_ID));
            repository.save(row);
        } catch (RuntimeException e) {
            // Losing an audit row is bad; failing the patient's booking because
            // the audit table is unavailable is worse. Log loudly and continue.
            log.error("agent.audit.write.failed tool[{}] outcome[{}]", toolName, outcome, e);
        }
    }
}
