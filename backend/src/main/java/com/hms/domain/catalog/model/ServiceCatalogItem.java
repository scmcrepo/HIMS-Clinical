package com.hms.domain.catalog.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity 
@Table(name = "service_catalog_items") 
@Getter 
@Setter 
@NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "(branch_id IS NULL OR branch_id = :branchId)")
public class ServiceCatalogItem extends AuditableEntity {

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

    public void addPricingTier(PricingTier tier) { 
        tier.setServiceCatalogItem(this); 
        pricingTiers.add(tier); 
    }

    public void removePricingTier(PricingTier tier) { 
        pricingTiers.remove(tier); 
        tier.setServiceCatalogItem(null); 
    }
}
