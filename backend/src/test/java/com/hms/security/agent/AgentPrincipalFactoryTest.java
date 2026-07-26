package com.hms.security.agent;

import com.hms.application.agent.AgentScope;
import com.hms.infrastructure.persistence.agent.AgentApiTokenEntity;
import com.hms.security.HmsUserDetails;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-001 / T-005.
 *
 * <p>This is the seam the whole agent design rests on: token scopes become
 * {@code featureKeys}, which become authorities, which {@code
 * HmsPermissionEvaluator} matches. If a refactor breaks the mapping, agent
 * authorization silently fails open or closed — so it is pinned here.
 */
class AgentPrincipalFactoryTest {

    private AgentApiTokenEntity token(UUID tenantId, UUID branchId, String... scopes) {
        AgentApiTokenEntity t = new AgentApiTokenEntity();
        t.setScopes(new LinkedHashSet<>(Set.of(scopes)));
        t.setTenantId(tenantId);
        t.setBranchId(branchId);
        return t;
    }

    @Test
    void scopesBecomeFeatureKeysAndThereforeAuthorities() {
        UUID tenant = UUID.randomUUID();
        HmsUserDetails principal = AgentPrincipalFactory.from(
            token(tenant, null, AgentScope.BED_READ, AgentScope.SCHEDULING_READ));

        Set<String> authorities = principal.getAuthorities().stream()
            .map(a -> a.getAuthority()).collect(Collectors.toSet());

        assertTrue(authorities.contains(AgentScope.BED_READ));
        assertTrue(authorities.contains(AgentScope.SCHEDULING_READ));
    }

    @Test
    void theTenantIsCarriedFromTheTokenNotInferred() {
        UUID tenant = UUID.randomUUID();
        assertEquals(tenant, AgentPrincipalFactory.from(
            token(tenant, null, AgentScope.BED_READ)).getTenantId());
    }

    @Test
    void aBranchPinnedTokenCarriesItsBranch() {
        UUID branch = UUID.randomUUID();
        assertEquals(branch, AgentPrincipalFactory.from(
            token(UUID.randomUUID(), branch, AgentScope.BED_READ)).getBranchId());
    }

    @Test
    void anAgentPrincipalIsNeverASuperAdmin() {
        // isSuperAdmin() bypasses every permission check; an agent reaching that
        // state would have unrestricted cross-tenant access.
        HmsUserDetails principal = AgentPrincipalFactory.from(
            token(UUID.randomUUID(), null, AgentScope.BED_READ));
        assertFalse(principal.isSuperAdmin());
    }

    @Test
    void aTokenWithNoScopesGrantsNoFeatureAuthorities() {
        HmsUserDetails principal = AgentPrincipalFactory.from(token(UUID.randomUUID(), null));
        Set<String> featureAuthorities = principal.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> !a.startsWith("ROLE_"))
            .collect(Collectors.toSet());
        assertTrue(featureAuthorities.isEmpty());
    }

    @Test
    void theUsernameIsStableAndAttributable() {
        // Agent actions land in the existing audit columns, so the principal
        // needs a stable identifier without inventing a users row.
        AgentApiTokenEntity t = token(UUID.randomUUID(), null, AgentScope.BED_READ);
        t.setId(UUID.randomUUID());
        assertEquals("agent:" + t.getId(), AgentPrincipalFactory.from(t).getUsername());
    }
}
