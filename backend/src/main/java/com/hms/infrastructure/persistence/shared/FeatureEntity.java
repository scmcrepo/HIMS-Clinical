package com.hms.infrastructure.persistence.shared;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.hms.infrastructure.persistence.tenant.TenantEntity;
import java.util.UUID;

@Entity @Table(name = "features") @Getter @Setter
public class FeatureEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;

    // Global unique on feature_key dropped in V096; now unique per-tenant
    // (uq_features_tenant_key).
    @Column(name = "feature_key", nullable = false, length = 80) private String featureKey;
    @Column(name = "description", length = 255) private String description;
    @Column(name = "module", length = 60) private String module;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private TenantEntity tenant;
}
