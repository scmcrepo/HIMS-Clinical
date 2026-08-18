package com.hms.application.portal;

import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import com.hms.infrastructure.tenant.TenantResolutionFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Runs a block of work inside an explicitly chosen tenant (and optionally
 * branch) scope, with the Hibernate filters enabled exactly as
 * {@code TenantResolutionFilter} would have set them.
 *
 * <p>Two portal paths need this, and both are cases the normal request-scoped
 * mechanism cannot serve:
 *
 * <ul>
 *   <li><b>Candidate enrichment.</b> The cross-tenant lookup returns bare ids
 *       spanning several tenants. Reading the display fields for each candidate
 *       must happen <em>inside</em> that candidate's tenant scope, so the
 *       decrypting read stays subject to the same filter every other read is —
 *       rather than one unscoped query fetching rows from four hospitals at
 *       once.</li>
 *   <li><b>Self-registration.</b> The caller holds an identity token and
 *       therefore has no tenant context at all. {@code AuditableEntity}'s
 *       {@code @PrePersist} stamps {@code tenantId} from {@link TenantContext},
 *       so without this the new patient row would be written with a null tenant
 *       — invisible to every subsequent tenant-filtered query, and a
 *       {@code NOT NULL} violation if the column enforces it.</li>
 * </ul>
 *
 * <p>The previous context is captured and restored rather than cleared, because
 * this can be called from a request that already has scope. Clearing would
 * silently strip the tenant from the remainder of that request.
 *
 * <p>This class widens no permissions: it narrows an unscoped thread to one
 * tenant. It must never be given a tenant id chosen by the client without the
 * server first checking that the client is entitled to it — in both call sites
 * the id comes from a server-side candidate set or an explicitly validated
 * registration target.
 */
@Component
@Slf4j
public class PortalTenantScope {

    @PersistenceContext
    private EntityManager entityManager;

    public <T> T call(UUID tenantId, UUID branchId, Supplier<T> work) {
        if (tenantId == null) {
            throw new IllegalArgumentException("PortalTenantScope requires a tenant id");
        }

        UUID previousTenant = TenantContext.get();
        UUID previousBranch = BranchContext.get();
        boolean previousSuperAdmin = TenantContext.isSuperAdmin();

        try {
            TenantContext.set(tenantId);
            // A portal principal is never a superadmin. Setting this explicitly
            // guards against inheriting the flag from an outer context.
            TenantContext.setSuperAdmin(false);
            enableFilter(TenantResolutionFilter.TENANT_FILTER_NAME,
                TenantResolutionFilter.TENANT_PARAM, tenantId);

            if (branchId != null) {
                BranchContext.set(branchId);
                enableFilter(TenantResolutionFilter.BRANCH_FILTER_NAME,
                    TenantResolutionFilter.BRANCH_PARAM, branchId);
            }

            return work.get();

        } finally {
            disableFilter(TenantResolutionFilter.BRANCH_FILTER_NAME);
            disableFilter(TenantResolutionFilter.TENANT_FILTER_NAME);

            if (previousTenant != null) {
                TenantContext.set(previousTenant);
                TenantContext.setSuperAdmin(previousSuperAdmin);
                enableFilter(TenantResolutionFilter.TENANT_FILTER_NAME,
                    TenantResolutionFilter.TENANT_PARAM, previousTenant);
            } else {
                TenantContext.clear();
            }

            if (previousBranch != null) {
                BranchContext.set(previousBranch);
                enableFilter(TenantResolutionFilter.BRANCH_FILTER_NAME,
                    TenantResolutionFilter.BRANCH_PARAM, previousBranch);
            } else {
                BranchContext.clear();
            }
        }
    }

    public void run(UUID tenantId, UUID branchId, Runnable work) {
        call(tenantId, branchId, () -> {
            work.run();
            return null;
        });
    }

    private void enableFilter(String name, String param, UUID value) {
        entityManager.unwrap(Session.class).enableFilter(name).setParameter(param, value);
    }

    private void disableFilter(String name) {
        try {
            entityManager.unwrap(Session.class).disableFilter(name);
        } catch (RuntimeException e) {
            log.debug("portal.scope.disable_filter_failed name={}", name);
        }
    }
}
