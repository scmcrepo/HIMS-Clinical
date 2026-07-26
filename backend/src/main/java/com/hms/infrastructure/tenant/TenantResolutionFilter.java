package com.hms.infrastructure.tenant;

import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.BranchJpaRepository;
import com.hms.infrastructure.observability.CorrelationIdFilter;
import com.hms.security.HmsUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    public static final String TENANT_FILTER_NAME = "tenantFilter";
    public static final String TENANT_PARAM = "tenantId";
    public static final String BRANCH_FILTER_NAME = "branchFilter";
    public static final String BRANCH_PARAM = "branchId";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String BRANCH_HEADER = "X-Branch-Id";

    @PersistenceContext
    private EntityManager entityManager;

    private final BranchJpaRepository branchRepo;

    public TenantResolutionFilter(@Lazy BranchJpaRepository branchRepo) {
        this.branchRepo = branchRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof HmsUserDetails user) {

                if (user.isSuperAdmin()) {
                    TenantContext.setSuperAdmin(true);
                    UUID impersonatedTenant = parseUuid(request.getHeader(TENANT_HEADER), TENANT_HEADER);
                    if (impersonatedTenant != null) {
                        TenantContext.set(impersonatedTenant);
                        enableTenantFilter(impersonatedTenant);
                        UUID branchId = parseUuid(request.getHeader(BRANCH_HEADER), BRANCH_HEADER);
                        if (branchId != null && branchBelongsToTenant(branchId, impersonatedTenant)) {
                            BranchContext.set(branchId);
                            enableBranchFilter(branchId);
                        }
                    }
                } else {
                    UUID tenantId = user.getTenantId();
                    if (tenantId == null) {
                        log.error("Authenticated non-superadmin user {} has no tenant; denying.",
                                  user.getUsername());
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "No tenant assigned");
                        return;
                    }
                    TenantContext.set(tenantId);
                    enableTenantFilter(tenantId);
                    org.slf4j.MDC.put(CorrelationIdFilter.MDC_TENANT_ID, tenantId.toString());

                    UUID branchId = user.getBranchId();
                    UUID requested = parseUuid(request.getHeader(BRANCH_HEADER), BRANCH_HEADER);
                    if (requested != null) {
                        boolean isAuthorized = false;
                        if (user.isHospitalAdmin()) {
                            isAuthorized = branchBelongsToTenant(requested, tenantId);
                            log.info("TenantResolutionFilter: user {} isHospitalAdmin=true, requested branch {}, belongsToTenant={}", user.getUsername(), requested, isAuthorized);
                        } else {
                            isAuthorized = user.getAuthorizedBranchIds() != null && user.getAuthorizedBranchIds().contains(requested);
                            log.info("TenantResolutionFilter: user {} isHospitalAdmin=false, requested branch {}, authorizedBranchIds={}, contains={}", user.getUsername(), requested, user.getAuthorizedBranchIds(), isAuthorized);
                        }
                        if (!isAuthorized) {
                            log.warn("Access denied for user {} to branch {}", user.getUsername(), requested);
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access to requested branch is denied");
                            return;
                        }
                        BranchContext.set(requested);
                        enableBranchFilter(requested);
                        org.slf4j.MDC.put(CorrelationIdFilter.MDC_BRANCH_ID, requested.toString());
                    } else if (branchId != null) {
                        BranchContext.set(branchId);
                        enableBranchFilter(branchId);
                        org.slf4j.MDC.put(CorrelationIdFilter.MDC_BRANCH_ID, branchId.toString());
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            disableFilter(BRANCH_FILTER_NAME);
            disableFilter(TENANT_FILTER_NAME);
            BranchContext.clear();
            TenantContext.clear();
        }
    }

    private boolean branchBelongsToTenant(UUID branchId, UUID tenantId) {
        boolean ok = branchRepo.findByIdAndTenantId(branchId, tenantId)
                .map(BranchEntity::isActive).orElse(false);
        if (!ok) log.warn("Ignoring {} header: branch {} not in tenant {}", BRANCH_HEADER, branchId, tenantId);
        return ok;
    }

    private void enableTenantFilter(UUID tenantId) {
        entityManager.unwrap(Session.class).enableFilter(TENANT_FILTER_NAME).setParameter(TENANT_PARAM, tenantId);
    }

    private void enableBranchFilter(UUID branchId) {
        entityManager.unwrap(Session.class).enableFilter(BRANCH_FILTER_NAME).setParameter(BRANCH_PARAM, branchId);
    }

    private void disableFilter(String name) {
        try {
            if (entityManager != null && entityManager.isOpen()) {
                entityManager.unwrap(Session.class).disableFilter(name);
            }
        } catch (Exception ignored) {
        }
    }

    private UUID parseUuid(String raw, String header) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring malformed {} header: {}", header, raw);
            return null;
        }
    }
}
