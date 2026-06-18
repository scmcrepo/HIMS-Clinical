package com.hms.domain.inventory.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name = "tax_categories") @Getter @Setter @NoArgsConstructor
public class TaxCategory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;
    @Column(name = "tax_id", insertable = false, updatable = false) private UUID taxId;
    @Column(name = "name", nullable = false, length = 60) private String name;
    @Column(name = "rate", precision = 6, scale = 2) private BigDecimal rate = BigDecimal.ZERO;
    @Column(name = "tenant_id", updatable = false) private UUID tenantId;

    @PrePersist
    void stampScope() {
        if (tenantId == null) {
            tenantId = com.hms.infrastructure.tenant.TenantContext.get();
        }
    }

    @PostLoad
    void assertScopeMatches() {
        UUID activeTenant = com.hms.infrastructure.tenant.TenantContext.get();
        if (activeTenant != null && tenantId != null && !activeTenant.equals(tenantId)) {
            throw new com.hms.exception.CrossTenantAccessException(
                "Attempted cross-tenant access to entity " + getClass().getSimpleName() + " " + id);
        }
    }
}
