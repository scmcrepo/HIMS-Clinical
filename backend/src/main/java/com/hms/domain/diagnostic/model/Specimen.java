package com.hms.domain.diagnostic.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

@Entity 
@Table(name = "specimens") 
@Getter 
@Setter 
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class Specimen extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "description", columnDefinition = "TEXT") private String description;
}

