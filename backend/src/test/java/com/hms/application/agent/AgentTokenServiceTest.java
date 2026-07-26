package com.hms.application.agent;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.agent.AgentApiTokenEntity;
import com.hms.infrastructure.persistence.agent.AgentApiTokenJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WO-001 / T-004.
 *
 * <p>The property that matters most here is that the plaintext token never
 * reaches the database. Everything else about the token lifecycle is recoverable;
 * a stored credential is not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentTokenServiceTest {

    @Mock
    private AgentApiTokenJpaRepository repository;

    @InjectMocks
    private AgentTokenService service;

    private AgentApiTokenEntity echoSave() {
        when(repository.save(any(AgentApiTokenEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        return null;
    }

    @Test
    void theStoredRowNeverContainsThePlaintextToken() {
        echoSave();
        AgentTokenService.IssuedToken issued =
            service.issue("scheduler", Set.of(AgentScope.BED_READ), null, null);

        ArgumentCaptor<AgentApiTokenEntity> captor =
            ArgumentCaptor.forClass(AgentApiTokenEntity.class);
        verify(repository).save(captor.capture());

        String plaintext = issued.plaintext();
        AgentApiTokenEntity saved = captor.getValue();

        assertNotEquals(plaintext, saved.getTokenHash());
        assertFalse(saved.getTokenHash().contains(plaintext),
            "the plaintext must not appear anywhere in the persisted row");
        assertEquals(64, saved.getTokenHash().length(), "SHA-256 hex is 64 chars");
    }

    @Test
    void theTokenCarriesAGreppablePrefix() {
        // So a leaked credential is findable in logs, repos and secret scanners.
        echoSave();
        assertTrue(service.issue("x", Set.of(AgentScope.BED_READ), null, null)
                       .plaintext().startsWith(AgentTokenService.TOKEN_PREFIX));
    }

    @Test
    void twoIssuedTokensAreDifferent() {
        echoSave();
        String a = service.issue("a", Set.of(AgentScope.BED_READ), null, null).plaintext();
        String b = service.issue("b", Set.of(AgentScope.BED_READ), null, null).plaintext();
        assertNotEquals(a, b);
    }

    @Test
    void unknownScopesAreRejectedAtIssueTime() {
        // A typo'd scope would otherwise fail later, confusingly, far from here.
        assertThrows(BusinessRuleViolationException.class,
            () -> service.issue("x", Set.of("AGENT_NOT_A_REAL_SCOPE"), null, null));
        verify(repository, never()).save(any());
    }

    @Test
    void tokenManageCannotBeIssuedToAnAgent() {
        // An agent that can mint tokens can escalate its own scopes.
        assertThrows(BusinessRuleViolationException.class,
            () -> service.issue("x", Set.of(AgentScope.TOKEN_MANAGE), null, null));
    }

    @Test
    void anEmptyScopeSetIsRejected() {
        assertThrows(BusinessRuleViolationException.class,
            () -> service.issue("x", Set.of(), null, null));
    }

    @Test
    void aBlankNameIsRejected() {
        assertThrows(BusinessRuleViolationException.class,
            () -> service.issue("  ", Set.of(AgentScope.BED_READ), null, null));
    }

    @Test
    void defaultValidityIsNinetyDays() {
        echoSave();
        AgentTokenService.IssuedToken issued =
            service.issue("x", Set.of(AgentScope.BED_READ), null, null);
        long days = Duration.between(Instant.now(), issued.entity().getExpiresAt()).toDays();
        assertTrue(days >= 89 && days <= 90, "expected ~90 days, got " + days);
    }

    @Test
    void anExpiredTokenDoesNotResolve() {
        AgentApiTokenEntity expired = new AgentApiTokenEntity();
        expired.setTokenHash(AgentTokenService.hash("hms_agt_x"));
        expired.setExpiresAt(Instant.now().minusSeconds(60));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertTrue(service.resolveUsable("hms_agt_x").isEmpty());
    }

    @Test
    void aRevokedTokenDoesNotResolve() {
        AgentApiTokenEntity revoked = new AgentApiTokenEntity();
        revoked.setTokenHash(AgentTokenService.hash("hms_agt_x"));
        revoked.setExpiresAt(Instant.now().plusSeconds(3600));
        revoked.setRevokedAt(Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertTrue(service.resolveUsable("hms_agt_x").isEmpty());
    }

    @Test
    void aTokenWithoutThePrefixIsNotEvenLookedUp() {
        assertTrue(service.resolveUsable("Bearer-something-else").isEmpty());
        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    void revocationIsIdempotent() {
        AgentApiTokenEntity token = new AgentApiTokenEntity();
        token.setRevokedAt(Instant.now().minusSeconds(10));
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token));

        service.revoke(id, UUID.randomUUID());
        verify(repository, never()).save(any());
    }

    @Test
    void lastUsedIsThrottledRatherThanWrittenEveryRequest() {
        // Otherwise every tool call costs a database write.
        AgentApiTokenEntity token = new AgentApiTokenEntity();
        token.setLastUsedAt(Instant.now().minusSeconds(5));
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token));

        service.touch(id);
        verify(repository, never()).save(any());
    }

    @Test
    void lastUsedIsWrittenOnceTheThrottleWindowHasPassed() {
        AgentApiTokenEntity token = new AgentApiTokenEntity();
        token.setLastUsedAt(Instant.now().minusSeconds(120));
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token));
        echoSave();

        service.touch(id);
        verify(repository).save(any());
    }
}
