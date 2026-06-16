package com.hms.infrastructure.tenant;

import java.util.UUID;

/**
 * Thread-local holder for the current request's branch id (the location within a tenant).
 *
 * <p>Branch is the finest data-isolation unit. A tenant (hospital) owns one or more branches;
 * the first branch is created automatically with the tenant.
 *
 * <p>Contract:
 * <ul>
 *   <li>Branch-pinned staff (doctors, nurses, reception, billing, lab, branch admin) =>
 *       {@code get()} returns their branch id; the Hibernate {@code branchFilter} is enabled.</li>
 *   <li>A HOSPITAL_ADMIN with no impersonation header => {@code get()} is {@code null} and the
 *       branch filter stays disabled, so they see every branch in their tenant (tenant filter
 *       still applies).</li>
 *   <li>A SUPERADMIN with no impersonation => {@code null}; no tenant or branch filter.</li>
 *   <li>Either a HOSPITAL_ADMIN or SUPERADMIN may pin a branch for the request via the
 *       {@code X-Branch-Id} header (validated against their tenant).</li>
 * </ul>
 *
 * <p>Always cleared in a finally block by {@link TenantResolutionFilter}.
 */
public final class BranchContext {

    private static final ThreadLocal<UUID> BRANCH_ID = new ThreadLocal<>();

    private BranchContext() {}

    public static UUID get() {
        return BRANCH_ID.get();
    }

    public static void set(UUID id) {
        BRANCH_ID.set(id);
    }

    /** True when the request is pinned to a single branch (filter should be active). */
    public static boolean isBranchScoped() {
        return BRANCH_ID.get() != null;
    }

    /**
     * @return the current branch id, or throws if none is set. Use in branch-scoped write paths
     *         where a missing branch is a programming error (e.g. branch-admin CRUD).
     */
    public static UUID require() {
        UUID id = BRANCH_ID.get();
        if (id == null) {
            throw new IllegalStateException(
                "No branch in context. This operation requires a branch-scoped user "
                + "(or a HOSPITAL_ADMIN/SUPERADMIN supplying X-Branch-Id).");
        }
        return id;
    }

    public static void clear() {
        BRANCH_ID.remove();
    }
}
