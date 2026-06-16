package com.hms.domain.shared.model;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

@Entity @Table(name = "account_units") @Getter @Setter @NoArgsConstructor
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "1=1")
public class AccountUnit extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "code", length = 20) private String code;
}

