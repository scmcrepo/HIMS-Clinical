package com.hms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * WO-001 / T-001.
 *
 * <p>The evaluator used to append a line to a hardcoded developer path on every
 * permission check. That was synchronous file I/O on the hot authorization path,
 * writing username and tenant outside the logging framework — so it was invisible
 * to Promtail/Loki, never rotated, and never masked.
 *
 * <p>These tests pin the fix: authorization decisions are unchanged, and no file
 * is written.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HmsPermissionEvaluatorTest {

    /** The path the old implementation wrote to on every single check. */
    private static final Path LEGACY_DEBUG_PATH =
        Path.of("/home/ssb/Desktop/HIMS-Clinical/backend/evaluator_debug.log");

    @Mock
    private FeaturePermissionCacheService cache;

    @InjectMocks
    private HmsPermissionEvaluator evaluator;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    private Authentication authFor(UUID tenant, Set<String> featureKeys, Set<String> roles) {
        HmsUserDetails principal = new HmsUserDetails(
            UUID.randomUUID(), "agent-user", "{noop}x", false,
            featureKeys, roles, Set.of(UUID.randomUUID()),
            null, null, tenant, null);
        return new UsernamePasswordAuthenticationToken(
            principal, "x", principal.getAuthorities());
    }

    @Test
    void writesNothingToTheLegacyDebugPath() throws Exception {
        // If the file already exists on this machine (a developer's box, say), the
        // assertion is on its content not growing rather than on its absence.
        long before = Files.exists(LEGACY_DEBUG_PATH) ? Files.size(LEGACY_DEBUG_PATH) : -1L;

        when(cache.isAllowed(any(), anyString(), any())).thenReturn(true);
        Authentication auth = authFor(tenantId, Set.of("APPOINTMENT"), Set.of("RECEPTION"));

        for (int i = 0; i < 50; i++) {
            evaluator.hasPermission(auth, "", "APPOINTMENT");
        }

        if (before < 0) {
            assertFalse(Files.exists(LEGACY_DEBUG_PATH),
                "Permission checks must not create files. The hardcoded debug write "
                + "was removed in T-001 and must not come back.");
        } else {
            org.junit.jupiter.api.Assertions.assertEquals(before, Files.size(LEGACY_DEBUG_PATH),
                "Permission checks must not append to any file.");
        }
    }

    @Test
    void allowsWhenTheRoleCacheGrantsTheFeature() {
        when(cache.isAllowed(any(), anyString(), any())).thenReturn(true);
        Authentication auth = authFor(tenantId, Set.of(), Set.of("RECEPTION"));

        assertTrue(evaluator.hasPermission(auth, "", "APPOINTMENT"));
    }

    @Test
    void allowsWhenTheAuthorityCarriesTheFeatureKeyDirectly() {
        // This fallback is what lets a scoped agent token (T-005) carry its scopes
        // as feature keys without needing role_features rows. Pinning it here so a
        // future refactor of the evaluator cannot silently break agent auth.
        when(cache.isAllowed(any(), anyString(), any())).thenReturn(false);
        Authentication auth = authFor(tenantId, Set.of("AGENT_BED_READ"), Set.of("AGENT"));

        assertTrue(evaluator.hasPermission(auth, "", "AGENT_BED_READ"));
    }

    @Test
    void deniesWhenNeitherCacheNorAuthorityGrantsTheFeature() {
        when(cache.isAllowed(any(), anyString(), any())).thenReturn(false);
        Authentication auth = authFor(tenantId, Set.of("AGENT_BED_READ"), Set.of("AGENT"));

        assertFalse(evaluator.hasPermission(auth, "", "AGENT_SCHEDULING_WRITE"));
    }

    @Test
    void deniesUnauthenticatedAndNonHmsPrincipals() {
        assertFalse(evaluator.hasPermission(null, "", "APPOINTMENT"));

        Authentication plain = new UsernamePasswordAuthenticationToken("bob", "x", Set.of());
        assertFalse(evaluator.hasPermission(plain, "", "APPOINTMENT"));
    }

    @Test
    void superAdminBypassesTheFeatureCheck() {
        // A SUPERADMIN is tenantId == null AND the SUPERADMIN role. Both are required,
        // so a tenant-scoped account merely named SUPERADMIN gains nothing.
        Authentication auth = authFor(null, Set.of(), Set.of("SUPERADMIN"));

        assertTrue(evaluator.hasPermission(auth, "", "ANYTHING_AT_ALL"));
    }

    @Test
    void aTenantScopedAccountNamedSuperadminDoesNotBypass() {
        when(cache.isAllowed(any(), anyString(), any())).thenReturn(false);
        Authentication auth = authFor(tenantId, Set.of(), Set.of("SUPERADMIN"));

        assertFalse(evaluator.hasPermission(auth, "", "APPOINTMENT"));
    }

    @Test
    void deniesWhenNoFeatureKeyCanBeResolved() {
        Authentication auth = authFor(tenantId, Set.of(), Set.of("RECEPTION"));

        assertFalse(evaluator.hasPermission(auth, "", ""));
        assertFalse(evaluator.hasPermission(auth, null, null));
    }
}
