package com.hms.domain.shared.model;
import jakarta.persistence.*;
import lombok.*;
import com.hms.domain.charge.model.Charge;
import com.hms.domain.diagnostic.model.DiagnosticType;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity @Table(name = "categories") @Getter @Setter @NoArgsConstructor
public class Category extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "category_type", length = 50)
    private String categoryType;

    @Column(name = "type")
    @Enumerated(EnumType.ORDINAL)
    private CategoryType type;

    @Column(name = "param_value")
    private String paramValue;

    @Column(name = "charge_category_type")
    @Enumerated(EnumType.ORDINAL)
    private ChargeCategoryType chargeCategoryType;

    @Column(name = "sub_type")
    @Enumerated(EnumType.ORDINAL)
    private ChargeCategoryType subType;

    @Transient
    @JsonIgnore
    private Charge charge;

    @Column(name = "diagnostic_type")
    @Enumerated(EnumType.ORDINAL)
    private DiagnosticType diagnosticType;

    @PrePersist
    @PreUpdate
    public void syncCategoryType() {
        if (this.type != null) {
            this.categoryType = this.type.name();
        }
    }
}

