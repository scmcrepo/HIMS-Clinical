package com.hms.application.agent;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.agent.AgentApiTokenEntity;
import com.hms.infrastructure.persistence.agent.AgentApiTokenJpaRepository;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Issue, list and revoke agent credentials.
 *
 * <p>Session-cookie authentication was ruled out for machine clients because
 * {@code SecurityConfig} sets {@code maximumSessions(1)} with
 * {@code maxSessionsPreventsLogin(true)}: one session per user, and the second
 * login is refused rather than evicting the first. An agent holding a session
 * could therefore never run more than one process. Scoped tokens on a stateless
 * filter chain are the answer, not a preference.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTokenService {

    /** Greppable prefix, so a leaked credential is findable in logs and repos. */
    public static final String TOKEN_PREFIX = "hms_agt_";

    private static final int TOKEN_BYTES = 32;
    private static final Duration DEFAULT_VALIDITY = Duration.ofDays(90);

    private final AgentApiTokenJpaRepository repository;
    private final SecureRandom random = new SecureRandom();

    /** Plaintext token plus its stored record. The plaintext is never persisted. */
    public record IssuedToken(AgentApiTokenEntity entity, String plaintext) {
    }

    @Transactional
    public IssuedToken issue(String name, Set<String> scopes, UUID branchId, Duration validity) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleViolationException("Token name is required");
        }
        Set<String> requested = new LinkedHashSet<>(scopes == null ? Set.of() : scopes);
        if (requested.isEmpty()) {
            throw new BusinessRuleViolationException("At least one scope is required");
        }
        // Reject unknown scopes rather than storing them. A token carrying a
        // typo'd scope fails later, confusingly, at a call site far from here.
        Set<String> unknown = new LinkedHashSet<>(requested);
        unknown.removeAll(AgentScope.ISSUABLE);
        if (!unknown.isEmpty()) {
            throw new BusinessRuleViolationException(
                "Not valid agent scopes: " + unknown + ". Issuable scopes are " + AgentScope.ISSUABLE);
        }

        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String plaintext = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        AgentApiTokenEntity entity = new AgentApiTokenEntity();
        entity.setName(name.trim());
        entity.setTokenHash(hash(plaintext));
        entity.setScopes(requested);
        entity.setExpiresAt(Instant.now().plus(validity == null ? DEFAULT_VALIDITY : validity));
        // Tenant is stamped by AuditableEntity's @PrePersist from TenantContext.
        // Branch is explicit: a null branch means a tenant-wide token.
        entity.setBranchId(branchId);

        AgentApiTokenEntity saved = repository.save(entity);
        log.info("agent.token.issued tokenId[{}] tenant[{}] branch[{}] scopes{} expires[{}]",
                 saved.getId(), TenantContext.get(), branchId, requested, saved.getExpiresAt());
        return new IssuedToken(saved, plaintext);
    }

    @Transactional(readOnly = true)
    public List<AgentApiTokenEntity> list() {
        // Tenant filter applies via AuditableEntity, so this is already scoped.
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void revoke(UUID tokenId, UUID revokedBy) {
        AgentApiTokenEntity token = repository.findById(tokenId)
            .orElseThrow(() -> new ResourceNotFoundException("Agent token not found: " + tokenId));
        if (token.isRevoked()) {
            return; // idempotent
        }
        token.setRevokedAt(Instant.now());
        token.setRevokedBy(revokedBy);
        repository.save(token);
        log.warn("agent.token.revoked tokenId[{}] tenant[{}] by[{}]",
                 tokenId, token.getTenantId(), revokedBy);
    }

    /**
     * Resolve a plaintext token to its record, if usable.
     *
     * <p>Runs before authentication, so there is no tenant context yet — the
     * token is what establishes it. Returns empty rather than throwing, and the
     * caller returns one generic 401 regardless of reason: telling an unauthorised
     * caller whether a token is unknown, expired or revoked is free reconnaissance.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AgentApiTokenEntity> resolveUsable(String plaintext) {
        if (plaintext == null || !plaintext.startsWith(TOKEN_PREFIX)) {
            return java.util.Optional.empty();
        }
        return repository.findByTokenHash(hash(plaintext))
            .filter(t -> t.isUsable(Instant.now()));
    }

    /**
     * Record use, throttled.
     *
     * <p>Writing on every request would add a DB write per tool call. Once a
     * minute is enough to answer "is this credential still in use", which is what
     * the field is for.
     */
    @Transactional
    public void touch(UUID tokenId) {
        repository.findById(tokenId).ifPresent(token -> {
            Instant now = Instant.now();
            Instant last = token.getLastUsedAt();
            if (last == null || Duration.between(last, now).toSeconds() >= 60) {
                token.setLastUsedAt(now);
                repository.save(token);
            }
        });
    }

    static String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Convenience for callers that do not care about branch pinning. */
    @Transactional
    public IssuedToken issue(String name, Set<String> scopes) {
        return issue(name, scopes, BranchContext.get(), DEFAULT_VALIDITY);
    }
}
