package com.hms.application.report.util;

import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tenant/branch isolation for raw-JDBC reports (audit finding 17.3).
 *
 * <p>Report SQL is executed through {@link org.springframework.jdbc.core.JdbcTemplate} and therefore
 * bypasses the Hibernate {@code tenantFilter}/{@code branchFilter}. This helper supplies the
 * predicate + positional args to scope each report query the same way:
 *
 * <pre>
 *   StringBuilder sql = new StringBuilder("SELECT ... FROM bills b WHERE b.bill_date BETWEEN ?::DATE AND ?::DATE");
 *   List&lt;Object&gt; args = new ArrayList&lt;&gt;(List.of(fromDate, toDate));
 *   sql.append(scope.predicate("b"));   // appends " AND b.tenant_id = ? [AND b.branch_id = ?]"
 *   args.addAll(scope.args());          // appends the matching values, in the same order
 *   sql.append(" ORDER BY ...");        // any trailing clauses (no new '?') go AFTER the predicate
 *   ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
 * </pre>
 *
 * <p>Scope rules (same as the Hibernate filters):
 * <ul>
 *   <li>SUPERADMIN, no impersonation → empty predicate / no args (platform-wide view).</li>
 *   <li>HOSPITAL_ADMIN (or SUPERADMIN pinning a tenant) → tenant predicate only (all branches).</li>
 *   <li>Branch-pinned staff → tenant + branch predicate.</li>
 * </ul>
 *
 * <p>IMPORTANT: append the predicate at the END of the WHERE conditions and {@code args.addAll} at
 * the END of the arg list (before any later-added args), so the positional '?' order stays aligned.
 */
@Component
public class ReportScope {

    /** SQL fragment to append to a query's WHERE for the given main-table alias. May be empty. */
    public String predicate(String alias) {
        StringBuilder sb = new StringBuilder();
        if (TenantContext.get() != null) sb.append(" AND ").append(alias).append(".tenant_id = ?");
        if (BranchContext.get() != null) sb.append(" AND ").append(alias).append(".branch_id = ?");
        return sb.toString();
    }

    /** Positional args matching {@link #predicate(String)}, in order. May be empty. */
    public List<Object> args() {
        List<Object> a = new ArrayList<>();
        UUID tenantId = TenantContext.get();
        UUID branchId = BranchContext.get();
        if (tenantId != null) a.add(tenantId);
        if (branchId != null) a.add(branchId);
        return a;
    }

    /** Convenience: are we in a scoped (non-platform) context? */
    public boolean isScoped() {
        return TenantContext.get() != null;
    }
}
