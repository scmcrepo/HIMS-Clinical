package com.hms.security.portal;

import com.hms.application.portal.PortalException;
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

/**
 * Authenticates a patient (or a number-verified pre-patient) from a portal JWT.
 *
 * <p>Sits on a stateless chain, like the agent filter, so the portal is not
 * subject to {@code maximumSessions(1)} — which would otherwise mean a patient
 * logging in on their phone silently killed the session of an unrelated staff
 * member, or of themselves on a tablet.
 *
 * <p>The two scopes produce structurally different principals:
 *
 * <ul>
 *   <li>{@code PORTAL_PATIENT} → an {@link HmsUserDetails} with a tenant and a
 *       branch, so {@code TenantResolutionFilter} downstream enables the
 *       Hibernate tenant and branch filters and every existing repository is
 *       scoped without modification.</li>
 *   <li>{@code PORTAL_IDENTITY} → a {@code PortalIdentityPrincipal}, which is
 *       deliberately <em>not</em> an {@code HmsUserDetails}. See the note on
 *       that type: an {@code HmsUserDetails} with a null tenant is rejected with
 *       403 by {@code TenantResolutionFilter}, so this is not a stylistic
 *       choice.</li>
 * </ul>
 *
 * <p>Endpoints that are genuinely public — requesting and verifying an OTP —
 * carry no token and are permitted by the chain configuration, not here.
 */
@Slf4j
@RequiredArgsConstructor
public class PortalTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final PortalTokenService tokenService;
    private final MeterRegistry meterRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // No token: let the chain decide. The OTP endpoints are permitAll and
        // must work unauthenticated; everything else is denied by the chain's
        // authorization rules rather than by a 401 invented here.
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        PortalTokenService.PortalClaims claims;
        try {
            claims = tokenService.verify(header.substring(BEARER.length()).trim());
        } catch (PortalException e) {
            reject(response, "invalid_token");
            return;
        } catch (RuntimeException e) {
            log.error("event=portal.auth.error exception={}", e.getClass().getSimpleName());
            reject(response, "verify_error");
            return;
        }

        try {
            if (claims.isPatientScope()) {
                if (claims.patientId() == null || claims.tenantId() == null
                        || claims.branchId() == null) {
                    // A patient-scope token missing its branch would build a
                    // principal that HmsUserDetails.isHospitalAdmin() reports
                    // true for — i.e. read access to every branch in the tenant.
                    reject(response, "incomplete_claims");
                    return;
                }
                HmsUserDetails principal = PortalPrincipalFactory.patient(
                    claims.patientId(), claims.tenantId(), claims.branchId());
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
                request.setAttribute("portalPatientId", claims.patientId());
                request.setAttribute("portalChainId", claims.chainId());

            } else {
                if (claims.contactNumberToken() == null) {
                    reject(response, "incomplete_claims");
                    return;
                }
                var principal = PortalPrincipalFactory.identity(claims.contactNumberToken());
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        principal, null, principal.authorities()));
            }

            meterRegistry.counter("hms_portal_auth_total", "outcome", "success").increment();
            chain.doFilter(request, response);

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        // One response shape for every rejection reason. The reason goes to the
        // log and the metric; telling the caller which check failed tells an
        // attacker which part of the forgery to correct.
        log.warn("event=portal.auth.failed reason={}", reason);
        meterRegistry.counter("hms_portal_auth_failures_total", "reason", reason).increment();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"message\":\"Invalid or expired portal credentials\","
            + "\"data\":{\"code\":\"UNAUTHORIZED\",\"retryable\":false}}");
    }
}
