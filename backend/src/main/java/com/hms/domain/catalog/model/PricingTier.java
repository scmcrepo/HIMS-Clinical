package com.hms.domain.catalog.model;
import com.hms.domain.billing.model.BillType;
import com.hms.domain.shared.model.AuditableEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "pricing_tiers") @Getter @Setter @NoArgsConstructor
public class PricingTier extends AuditableEntity {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_catalog_item_id", nullable = false) private ServiceCatalogItem serviceCatalogItem;
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "bill_type", nullable = false) private BillType billType;
    @Column(name = "unit_rate", nullable = false) private long unitRate = 0L;
    public long getRateForBillType(BillType type) { return billType == type ? unitRate : 0L; }
}
