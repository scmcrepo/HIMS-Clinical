package com.hms.domain.shared.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Entity 
@Table(name = "departments") 
@Getter 
@Setter 
@NoArgsConstructor
public class Department extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 100) 
    private String name;

    @Column(name = "department_type", length = 40) 
    private String departmentType;

    @Column(name = "stock_access", length = 20) 
    private String stockAccess;

    @Column(name = "display_order", length = 20) 
    private String displayOrder;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private Set<DepartmentCategories> departmentCategories = new HashSet<>();

    @ElementCollection(targetClass = StockDepartmentAccess.class, fetch = FetchType.EAGER)
    @JoinTable(name = "department_stock", joinColumns = @JoinColumn(name = "department_id"))
    @Column(name = "access_type")
    @Enumerated(EnumType.ORDINAL)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private Set<StockDepartmentAccess> stockDepartmentAccesses = new HashSet<>();

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private Set<DepartmentTemplate> departmentTemplates = new HashSet<>();

    @Transient
    public DepartmentType getType() {
        if (departmentType == null) return null;
        try {
            return DepartmentType.valueOf(departmentType);
        } catch (IllegalArgumentException e) {
            if ("CLINICAL".equalsIgnoreCase(departmentType)) return DepartmentType.Clinical;
            if ("DIAGNOSTICS".equalsIgnoreCase(departmentType)) return DepartmentType.Diagnostics;
            if ("STOCK".equalsIgnoreCase(departmentType)) return DepartmentType.Stock;
            if ("OTHER".equalsIgnoreCase(departmentType)) return DepartmentType.Other;
            return null;
        }
    }

    @Transient
    public void setType(DepartmentType type) {
        if (type != null) {
            this.departmentType = type.name();
        }
    }
}
