package com.hms.domain.inventory.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "molecules")
@Getter
@Setter
@NoArgsConstructor
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "1=1")
public class Molecule extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "cims_id", length = 100)
    private String cimsId;
}

