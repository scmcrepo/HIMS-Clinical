package com.hms.infrastructure.tenant;

import java.util.UUID;

/**
 * Thread-local holder for the current request's tenant id.
 *
 * <p>Contract:
 * <ul>
 *   <li>A normal authenticated user => the tenant id is set for the duration of the request.</li>
 *   <li>A SUPERADMIN with no impersonation header => {@code get()} returns {@code null}
 *       AND {@link #isSuperAdmin()} is {@code true}. Callers MUST treat null+superadmin as
 *       "platform / cross-tenant" and never as "all data is in-scope by default".</li>
 *   <li>Before authentication (e.g. the /auth/login request itself) => null + not superadmin.</li>
 * </ul>
 *
 * <p>Always cleared in a finally block by {@link TenantResolutionFilter}.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUPER_ADMIN = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TenantContext() {}

    public static UUID get() {
        return TENANT_ID.get();
    }

    public static void set(UUID id) {
        TENANT_ID.set(id);
    }

    public static boolean isSuperAdmin() {
        return Boolean.TRUE.equals(SUPER_ADMIN.get());
    }

    public static void setSuperAdmin(boolean superAdmin) {
        SUPER_ADMIN.set(superAdmin);
    }

    /**
     * @return the current tenant id, or throws if none is set and the caller is not a
     *         platform superadmin. Use this in tenant-scoped write paths where a missing
     *         tenant is a programming error.
     */
    public static UUID require() {
        UUID id = TENANT_ID.get();
        if (id == null) {
            throw new IllegalStateException(
                "No tenant in context. This operation requires a tenant-scoped user.");
        }
        return id;
    }

    public static void clear() {
        TENANT_ID.remove();
        SUPER_ADMIN.remove();
    }
}
