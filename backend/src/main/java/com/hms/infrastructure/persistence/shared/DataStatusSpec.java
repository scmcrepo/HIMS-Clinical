package com.hms.infrastructure.persistence.shared;

import com.hms.domain.shared.model.ReqDataStatus;

/**
 * Provides the SQL/JPQL WHERE fragment for ReqDataStatus filtering.
 * Usage: DataStatusSpec.whereClause(status) → embed in @Query
 *
 * Since JPA @Query doesn't support dynamic WHERE clauses,
 * we use separate repository methods per status case.
 * This class documents the intent and provides constants.
 */
public final class DataStatusSpec {
    private DataStatusSpec() {}

    /**
     * Returns the effective status filter as a short string for logging.
     * Controllers call the appropriate repository method based on this.
     */
    public static String describe(ReqDataStatus status) {
        if (status == null)                     return "active only (status=1)";
        if (status == ReqDataStatus.notDeleted) return "active+inactive (status!=2)";
        return "all records";
    }

    /** true if the entity should be included given the requested filter */
    public static boolean matches(ReqDataStatus filter, int entityStatus) {
        if (filter == null)                     return entityStatus == 1;       // active only
        if (filter == ReqDataStatus.notDeleted) return entityStatus != 2;       // not deleted
        return true;                                                              // all
    }
}
