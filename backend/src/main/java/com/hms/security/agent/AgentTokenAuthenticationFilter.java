package com.hms.security.agent;

import com.hms.application.agent.AgentTokenService;
import com.hms.infrastructure.persistence.agent.AgentApiTokenEntity;
import com.hms.security.HmsUserDetails;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates a machine client from a bearer token.
 *
 * <p>The design turns on one observation about the existing code: {@code
 * TenantResolutionFilter} runs after this point and simply reads whatever {@link
 * HmsUserDetails} is in the {@code SecurityContext}, while {@code
 * HmsPermissionEvaluator} falls back to matching the raw feature key against
 * {@code getAuthorities()} — which {@code HmsUserDetails} populates directly from
 * its {@code featureKeys}. So constructing an {@code HmsUserDetails} carrying the
 * token's tenant, branch and scopes gets tenant filtering, branch filtering, RBAC
 * and audit stamping for free, with no change to the 176 existing controllers.
 *
 * <p>Runs on a stateless chain. That is what dodges {@code maximumSessions(1)} /
 * {@code maxSessionsPreventsLogin(true)}, which would otherwise cap the whole
 * agent service at a single process.
 */
@Slf4j
@RequiredArgsConstructor
public class AgentTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AgentTokenService tokenService;
    private final MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            reject(response, "missing_bearer");
            return;
        }

        String plaintext = header.substring(BEARER.length()).trim();
        Optional<AgentApiTokenEntity> resolved;
        try {
            resolved = tokenService.resolveUsable(plaintext);
        } catch (RuntimeException e) {
            log.error("agent.auth.error while resolving token", e);
            reject(response, "resolve_error");
            return;
        }

        if (resolved.isEmpty()) {
            // One generic response whether the token is unknown, expired or
            // revoked. The distinction goes to the log, not to the caller.
            reject(response, "invalid_token");
            return;
        }

        AgentApiTokenEntity token = resolved.get();
        HmsUserDetails principal = AgentPrincipalFactory.from(token);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        request.setAttribute("agentTokenId", token.getId());

        try {
            tokenService.touch(token.getId());
        } catch (RuntimeException e) {
            // Usage tracking must never fail a request.
            log.debug("agent.token.touch.failed tokenId[{}]", token.getId(), e);
        }

        meterRegistry.counter("hms_agent_auth_total", "outcome", "success").increment();

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        log.warn("agent.auth.failed reason[{}]", reason);
        meterRegistry.counter("hms_agent_auth_failures_total", "reason", reason).increment();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"message\":\"Invalid or expired agent credentials\","
            + "\"data\":{\"code\":\"UNAUTHORIZED\",\"retryable\":false}}");
    }
}
