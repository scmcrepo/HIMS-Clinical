package com.hms.domain.catalog.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.infrastructure.persistence.tenant.BranchEntity;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity 
@Table(name = "service_catalog_items") 
@Getter 
@Setter 
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "(branch_id IS NULL OR branch_id = :branchId)")
public class ServiceCatalogItem {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    @JsonIgnore
    private TenantEntity tenant;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

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
    private Instant createdAt = Instant.now();

    @LastModifiedBy
    @Column(name = "modified_by")
    private UUID modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt = Instant.now();

    @Column(name = "name", nullable = false, length = 150) 
    private String name;

    @Column(name = "category_id", nullable = false) 
    private UUID categoryId;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "service_type", nullable = false) 
    private ServiceType serviceType = ServiceType.INDIVIDUAL;

    @Column(name = "requires_order", nullable = false) 
    private boolean requiresOrder = false;

    @OneToMany(mappedBy = "serviceCatalogItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PricingTier> pricingTiers = new ArrayList<>();

    @PrePersist
    void stampScope() {
        if (tenantId == null) {
            tenantId = TenantContext.get();
        }
        if (branchId == null) {
            branchId = BranchContext.get();
        }
    }

    @PostLoad
    void assertScopeMatches() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof com.hms.security.HmsUserDetails user) {
            if (user.isSuperAdmin()) {
                return;
            }
            UUID activeTenant = TenantContext.get();
            if (activeTenant != null && tenantId != null && !activeTenant.equals(tenantId)) {
                throw new com.hms.exception.CrossTenantAccessException(
                    "Attempted cross-tenant access to entity ServiceCatalogItem " + id);
            }
            if (user.isHospitalAdmin()) {
                return;
            }
        } else {
            UUID activeTenant = TenantContext.get();
            if (activeTenant != null && tenantId != null && !activeTenant.equals(tenantId)) {
                throw new com.hms.exception.CrossTenantAccessException(
                    "Attempted cross-tenant access to entity ServiceCatalogItem " + id);
            }
        }

        UUID activeBranch = BranchContext.get();
        if (activeBranch != null && branchId != null && !activeBranch.equals(branchId)) {
            throw new com.hms.exception.CrossTenantAccessException(
                "Attempted cross-branch access to entity ServiceCatalogItem " + id);
        }
    }

    public void addPricingTier(PricingTier tier) { 
        tier.setServiceCatalogItem(this); 
        pricingTiers.add(tier); 
    }

    public void removePricingTier(PricingTier tier) { 
        pricingTiers.remove(tier); 
        tier.setServiceCatalogItem(null); 
    }

    @JsonIgnore
    public boolean isActive()  { return status == EntityStatus.ACTIVE;  }

    @JsonIgnore
    public boolean isDeleted() { return status == EntityStatus.DELETED; }

    public void softDelete() { this.status = EntityStatus.DELETED;  }
    public void deactivate() { this.status = EntityStatus.INACTIVE; }
    public void activate()   { this.status = EntityStatus.ACTIVE;   }
}
