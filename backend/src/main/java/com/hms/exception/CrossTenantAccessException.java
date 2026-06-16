package com.hms.exception;

/**
 * Thrown when a request attempts to read or mutate an entity belonging to a tenant
 * other than the one in the active {@link com.hms.infrastructure.tenant.TenantContext}.
 * Map this to HTTP 403 in the global exception handler.
 */
public class CrossTenantAccessException extends RuntimeException {
    public CrossTenantAccessException(String message) {
        super(message);
    }
}
