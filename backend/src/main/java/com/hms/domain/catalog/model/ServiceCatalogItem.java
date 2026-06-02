package com.hms.domain.catalog.model;

import com.hms.domain.shared.model.EntityStatus;
import jakarta.persistence.*;
import lombok.*;
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
public class ServiceCatalogItem {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @PrePersist
    public void ensureId() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

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

    public boolean isActive()  { return status == EntityStatus.ACTIVE;  }
    public boolean isDeleted() { return status == EntityStatus.DELETED; }
    public void softDelete() { this.status = EntityStatus.DELETED;  }
    public void deactivate() { this.status = EntityStatus.INACTIVE; }
    public void activate()   { this.status = EntityStatus.ACTIVE;   }
}
