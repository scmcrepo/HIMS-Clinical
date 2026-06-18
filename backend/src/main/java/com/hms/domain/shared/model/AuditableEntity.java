package com.hms.domain.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for every persisted *business* entity in the HMS domain.
 *
 * <p><b>Multi-tenancy &amp; branches:</b> a {@code tenant_id} and a {@code branch_id} column are
 * added here so ALL business entities are scoped with a single change (they already extend this
 * class; Java single-inheritance makes a separate {@code TenantScopedEntity} superclass impossible).
 *
 * <p><b>Scope hierarchy:</b> tenant (hospital) &gt; branch (location) &gt; row. A SUPERADMIN sees
 * everything; a HOSPITAL_ADMIN sees every branch in their tenant (tenant filter only); branch staff
 * see only their branch (tenant + branch filters).
 *
 * <p>Enforcement, per scope, uses three mechanisms:
 * <ol>
 *   <li>{@code @Filter} — {@code tenantFilter} and {@code branchFilter} automatically narrow every
 *       list/search query. Activated per-request by {@code TenantResolutionFilter}: the tenant
 *       filter for any tenant user, the branch filter only when the user is branch-pinned.</li>
 *   <li>{@code @PrePersist} — stamps {@code tenantId} and {@code branchId} from the request
 *       contexts on insert, so callers never set them manually.</li>
 *   <li>{@code @PostLoad} — closes the findById gap (primary-key loads bypass {@code @Filter}):
 *       throws if a loaded row's tenant/branch does not match the active scope.</li>
 * </ol>
 *
 * <p>Global tables (sequences, system settings, tenants, branches) deliberately do NOT extend this.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@FilterDef(name = "branchFilter", parameters = @ParamDef(name = "branchId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "branch_id = :branchId")
public abstract class AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Raw tenant FK — the source of truth. Stamped automatically at persist time.
     * Kept as a plain column (not the association) so it can be set without an
     * EntityManager and so the Hibernate @Filter can reference {@code tenant_id} directly.
     */
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    /** Read-only navigation to the tenant; writes go through {@link #tenantId}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    @JsonIgnore
    private TenantEntity tenant;

    /**
     * Raw branch FK — the finest isolation key. Stamped automatically at persist time from
     * {@link BranchContext}. Nullable: rows created by a HOSPITAL_ADMIN acting tenant-wide
     * (no pinned branch and no {@code X-Branch-Id}) carry a null branch.
     */
    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    /** Read-only navigation to the branch; writes go through {@link #branchId}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    @JsonIgnore
    private BranchEntity branch;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private EntityStatus status = EntityStatus.ACTIVE;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "modified_by")
    private UUID modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    // ── Tenant / branch lifecycle ─────────────────────────────────────────────

    /** Stamp tenant and branch from the request contexts on insert (if not already set). */
    @PrePersist
    void stampScope() {
        if (tenantId == null) {
            tenantId = TenantContext.get(); // may be null for platform/superadmin writes
        }
        if (branchId == null) {
            boolean isTenantWide = false;
            org.hibernate.annotations.Filter[] filters = this.getClass().getAnnotationsByType(org.hibernate.annotations.Filter.class);
            for (org.hibernate.annotations.Filter f : filters) {
                if ("branchFilter".equals(f.name()) && "1=1".equals(f.condition())) {
                    isTenantWide = true;
                    break;
                }
            }
            if (!isTenantWide) {
                branchId = BranchContext.get(); // may be null for tenant-wide (hospital admin) writes
            }
        }
    }

    /**
     * Defend the findById / association-navigation gap: Hibernate @Filter does not apply to
     * primary-key loads, so verify the loaded row is within the active tenant AND (when the
     * request is branch-pinned) the active branch. Skipped when no concrete scope is in context
     * (platform/superadmin view, hospital-admin tenant-wide view, or the unauthenticated login).
     */
    @PostLoad
    void assertScopeMatches() {
        UUID activeTenant = TenantContext.get();
        if (activeTenant != null && tenantId != null && !activeTenant.equals(tenantId)) {
            throw new com.hms.exception.CrossTenantAccessException(
                "Attempted cross-tenant access to entity " + getClass().getSimpleName() + " " + id);
        }
        UUID activeBranch = BranchContext.get();
        if (activeBranch != null && branchId != null && !activeBranch.equals(branchId)) {
            throw new com.hms.exception.CrossTenantAccessException(
                "Attempted cross-branch access to entity " + getClass().getSimpleName() + " " + id);
        }
    }

    // ── Behaviour ─────────────────────────────────────────────────────────────

    @JsonIgnore
    public boolean isActive()  { return status == EntityStatus.ACTIVE;  }

    @JsonIgnore
    public boolean isDeleted() { return status == EntityStatus.DELETED; }

    public void softDelete() { this.status = EntityStatus.DELETED;  }
    public void deactivate() { this.status = EntityStatus.INACTIVE; }
    public void activate()   { this.status = EntityStatus.ACTIVE;   }
}
