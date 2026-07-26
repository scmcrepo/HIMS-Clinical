package com.hms.application.tenant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-001 / T-003.
 *
 * <p>V176 seeds the agent feature keys for every tenant that existed when it ran.
 * Tenants provisioned afterwards are covered only by
 * {@code TenantService.seedRbac()} — and forgetting that second half is the most
 * repeated failure mode in this codebase: the feature works perfectly in dev and
 * silently 403s for the next hospital onboarded.
 *
 * <p>These assertions are deliberately structural (reflecting over the static
 * catalogue) rather than integration-level, so they fail fast in the unit suite
 * without needing a database. The companion Testcontainers test asserts the
 * migration half.
 */
class TenantProvisioningAgentFeaturesTest {

    private static final Set<String> AGENT_FEATURES = Set.of(
        "AGENT_SCHEDULING_READ",
        "AGENT_SCHEDULING_WRITE",
        "AGENT_BILLING_READ",
        "AGENT_BED_READ",
        "AGENT_TOOLS_READ",
        "AGENT_TOKEN_MANAGE");

    /** Tool features an agent principal legitimately needs. */
    private static final Set<String> AGENT_TOOL_FEATURES = Set.of(
        "AGENT_SCHEDULING_READ",
        "AGENT_SCHEDULING_WRITE",
        "AGENT_BILLING_READ",
        "AGENT_BED_READ",
        "AGENT_TOOLS_READ");

    @SuppressWarnings("unchecked")
    private static <T> T staticField(String name) throws Exception {
        Field f = TenantService.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(null);
    }

    private static Set<String> catalogueKeys() throws Exception {
        List<String[]> features = staticField("FEATURES");
        return features.stream().map(f -> f[0]).collect(Collectors.toSet());
    }

    @Test
    void everyAgentFeatureIsInThePerTenantCatalogue() throws Exception {
        Set<String> keys = catalogueKeys();
        for (String key : AGENT_FEATURES) {
            assertTrue(keys.contains(key),
                key + " is seeded by V176 for existing tenants but missing from "
                    + "TenantService.FEATURES, so tenants provisioned from now on "
                    + "will not have it and every agent call will 403 for them.");
        }
    }

    @Test
    void agentFeaturesAreDeclaredUnderTheAgentModule() throws Exception {
        List<String[]> features = staticField("FEATURES");
        for (String[] f : features) {
            if (AGENT_FEATURES.contains(f[0])) {
                assertEquals("AGENT", f[1],
                    f[0] + " must sit in the AGENT module so V176's "
                        + "'ADMIN gets everything with module = AGENT' grant matches.");
            }
        }
    }

    @Test
    void theCatalogueHasNoDuplicateKeys() throws Exception {
        List<String[]> features = staticField("FEATURES");
        List<String> all = features.stream().map(f -> f[0]).toList();
        assertEquals(all.size(), Set.copyOf(all).size(),
            "duplicate feature keys would violate uq_features_tenant_key on seed");
    }

    @Test
    void theAgentRoleGrantsExactlyTheToolFeatures() throws Exception {
        Map<String, List<String>> grants = staticField("ROLE_GRANTS");
        List<String> agentGrants = grants.get("AGENT");

        assertTrue(agentGrants != null && !agentGrants.isEmpty(),
            "the AGENT role must exist in ROLE_GRANTS or agent principals get nothing");
        assertEquals(AGENT_TOOL_FEATURES, Set.copyOf(agentGrants));
    }

    @Test
    void theAgentRoleCannotMintItsOwnCredentials() throws Exception {
        Map<String, List<String>> grants = staticField("ROLE_GRANTS");

        assertFalse(grants.get("AGENT").contains("AGENT_TOKEN_MANAGE"),
            "an agent that can issue tokens can escalate its own scopes; "
                + "token management belongs to HOSPITAL_ADMIN and ADMIN only");
    }

    @Test
    void hospitalAdminCanManageAgentTokens() throws Exception {
        Map<String, List<String>> grants = staticField("ROLE_GRANTS");

        assertTrue(grants.get("HOSPITAL_ADMIN").contains("AGENT_TOKEN_MANAGE"),
            "an admin who cannot revoke a leaked agent credential without a DBA "
                + "is a security problem");
    }

    @Test
    void roleGrantsStillFitsWithinMapOfLimit() throws Exception {
        // Map.of() accepts at most 10 key/value pairs and fails at class-init time,
        // not compile time, if exceeded. Catch it here rather than at boot.
        Map<String, List<String>> grants = staticField("ROLE_GRANTS");
        assertTrue(grants.size() <= 10,
            "ROLE_GRANTS uses Map.of() which caps at 10 pairs — switch to "
                + "Map.ofEntries() before adding another role");
    }
}
