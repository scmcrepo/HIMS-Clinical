package com.hms.application.agent;

import java.util.Set;

/**
 * The vocabulary of agent token scopes.
 *
 * <p>Scopes are feature keys. Keeping one vocabulary rather than two means a
 * scope check and an RBAC check can never disagree, and it is why
 * {@code HmsPermissionEvaluator}'s authority fallback works for agent principals
 * without any role_features rows.
 */
public final class AgentScope {

    public static final String SCHEDULING_READ  = "AGENT_SCHEDULING_READ";
    public static final String SCHEDULING_WRITE = "AGENT_SCHEDULING_WRITE";
    public static final String BILLING_READ     = "AGENT_BILLING_READ";
    public static final String BED_READ         = "AGENT_BED_READ";
    public static final String TOOLS_READ       = "AGENT_TOOLS_READ";
    public static final String HITL_RAISE       = "AGENT_HITL_RAISE";
    public static final String ABHA_WRITE       = "AGENT_ABHA_WRITE";
    public static final String CLAIMS_READ      = "AGENT_CLAIMS_READ";

    /** Administrative. Deliberately NOT issuable to an agent token — an agent
     *  that can mint credentials can escalate its own scopes. */
    public static final String TOKEN_MANAGE     = "AGENT_TOKEN_MANAGE";

    public static final Set<String> ISSUABLE = Set.of(
        SCHEDULING_READ, SCHEDULING_WRITE, BILLING_READ, BED_READ, TOOLS_READ, HITL_RAISE,
        ABHA_WRITE, CLAIMS_READ);

    private AgentScope() {
    }
}
