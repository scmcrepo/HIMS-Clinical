package com.hms.domain.shared.model;

/**
 * Controls Hibernate-style filtering on list endpoints.
 * Mirrors legacy ReqDataStatus enum:
 *
 *   null / omitted → active records only (status = 1)
 *   notDeleted     → active + inactive (status != 2)
 *   all            → no filter (all records including deleted)
 *
 * Usage in JPQL: statusFilter(status, 'e')
 * Usage in repositories: pass through to query methods.
 */
public enum ReqDataStatus {
    notDeleted,
    all
}
