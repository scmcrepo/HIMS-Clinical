package com.hms.security.agent;

import com.hms.infrastructure.persistence.agent.AgentApiTokenEntity;
import com.hms.security.HmsUserDetails;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the {@link HmsUserDetails} an agent token authenticates as.
 *
 * <p>The token's scopes become {@code featureKeys}, which {@code HmsUserDetails}
 * exposes as authorities and {@code HmsPermissionEvaluator} matches against. This
 * is the seam that lets agent authorization reuse the existing RBAC machinery
 * rather than growing a parallel one.
 */
public final class AgentPrincipalFactory {

    public static final String AGENT_ROLE = "AGENT";

    private AgentPrincipalFactory() {
    }

    public static HmsUserDetails from(AgentApiTokenEntity token) {
        Set<String> featureKeys = new LinkedHashSet<>(
            token.getScopes() == null ? Set.of() : token.getScopes());

        // A synthetic, stable username so agent actions are attributable in the
        // existing audit columns without inventing a users row.
        String username = "agent:" + token.getId();

        return new HmsUserDetails(
            token.getId(),
            username,
            "",                       // no password; this principal never logs in
            false,                    // not locked
            featureKeys,
            Set.of(AGENT_ROLE),
            Set.<UUID>of(),           // no role ids: authorization is by scope
            null,                     // consultantId
            null,                     // departmentId
            token.getTenantId(),
            token.getBranchId());
    }
}
