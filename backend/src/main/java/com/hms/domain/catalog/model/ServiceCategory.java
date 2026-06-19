package com.hms.domain.catalog.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "service_categories") @Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "1=1")
public class ServiceCategory extends AuditableEntity {
    @Column(name = "name", nullable = false, unique = true, length = 100) private String name;
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "category_type", nullable = false) private ServiceCategoryType categoryType;
}
