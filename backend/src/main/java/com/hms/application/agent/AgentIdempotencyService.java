package com.hms.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.agent.AgentIdempotencyKeyEntity;
import com.hms.infrastructure.persistence.agent.AgentIdempotencyKeyJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Makes agent writes safe to retry.
 *
 * <p>LLM agents retry — on timeout, on a transport blip, on their own uncertainty.
 * Without this, a retried {@code book_slot} creates a second appointment and the
 * hospital discovers it when two people arrive for the same slot.
 *
 * <p>Concurrency is handled by the unique constraint rather than a read-then-write
 * check: two simultaneous requests with the same key both attempt an insert, one
 * wins, and the loser's constraint violation *is* the replay signal. A
 * check-then-act would let both through in the window between them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentIdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final AgentIdempotencyKeyJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public record Outcome<T>(T value, boolean replayed) {
    }

    /**
     * Execute once per key.
     *
     * @param key      caller-supplied idempotency key; required
     * @param toolName for auditing and for detecting key reuse across tools
     * @param request  used to fingerprint the request body
     * @param action   the real work
     */
    public <T> Outcome<T> execute(String key, String toolName, Object request,
                                  Supplier<T> action, Class<T> type) {
        return execute(key, toolName, request, action, type, null);
    }

    /**
     * As above, recording which patient the cached response concerns.
     *
     * <p>WO-024: {@code responseBody} is a serialised tool result and routinely
     * carries patient detail. Without {@code patientId} an erasure request cannot
     * reach it — the sweep has nothing to match on, and the cached copy outlives
     * the record it was derived from.
     *
     * @param patientId null for tool calls that touch no patient, such as a
     *                  bed-occupancy lookup
     */
    public <T> Outcome<T> execute(String key, String toolName, Object request,
                                  Supplier<T> action, Class<T> type,
                                  java.util.UUID patientId) {
        if (key == null || key.isBlank()) {
            throw new BusinessRuleViolationException(
                "X-Idempotency-Key is required for agent write operations");
        }
        String keyHash = sha256(key);
        String requestHash = sha256(serialise(request));

        Optional<AgentIdempotencyKeyEntity> existing = repository.findByKeyHash(keyHash);
        if (existing.isPresent()) {
            return replay(existing.get(), toolName, requestHash, type);
        }

        T result = action.get();

        try {
            AgentIdempotencyKeyEntity row = new AgentIdempotencyKeyEntity();
            row.setKeyHash(keyHash);
            row.setToolName(toolName);
            row.setRequestHash(requestHash);
            row.setResponseStatus(200);
            row.setResponseBody(serialise(result));
            row.setPatientId(patientId);
            row.setExpiresAt(Instant.now().plus(TTL));
            repository.save(row);
        } catch (DataIntegrityViolationException race) {
            // Another thread inserted the same key while we were working. Ours
            // already executed, so return it; the stored copy will be theirs.
            log.warn("agent.idempotency.race tool[{}]", toolName);
        }
        return new Outcome<>(result, false);
    }

    private <T> Outcome<T> replay(AgentIdempotencyKeyEntity row, String toolName,
                                  String requestHash, Class<T> type) {
        if (row.getRequestHash() != null && !row.getRequestHash().equals(requestHash)) {
            // Same key, different body. Almost always a caller bug, and silently
            // replaying the wrong response would hide it.
            throw new BusinessRuleViolationException(
                "Idempotency key already used for a different request");
        }
        meterRegistry.counter("hms_agent_idempotency_replays_total", "tool", toolName).increment();
        log.info("agent.idempotency.replayed tool[{}]", toolName);
        try {
            T value = objectMapper.readValue(row.getResponseBody(), type);
            return new Outcome<>(value, true);
        } catch (Exception e) {
            throw new BusinessRuleViolationException("Stored idempotent response is unreadable");
        }
    }

    /**
     * Purge expired keys.
     *
     * <p>Runs on a scheduled thread. Two consequences, both intentional:
     *
     * <p>First, there is no tenant context, so the Hibernate {@code tenantFilter}
     * inherited from {@code AuditableEntity} is not enabled on this session and
     * the delete spans every tenant. For a TTL purge keyed only on {@code
     * expires_at} that is exactly right — but it is the kind of thing a later
     * reader "fixes" by adding a tenant predicate, which would silently leave
     * every other tenant's expired keys to accumulate forever.
     *
     * <p>Second, do not read the tenant from context here. There is none to read,
     * and code that tries either throws or quietly proceeds unscoped.
     */
    @Scheduled(fixedDelayString = "PT1H")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeExpired() {
        try {
            int removed = repository.deleteExpired(Instant.now());
            if (removed > 0) {
                log.info("agent.idempotency.purged count[{}]", removed);
            }
        } catch (RuntimeException e) {
            log.error("agent.idempotency.purge.failed", e);
        }
    }

    private String serialise(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
