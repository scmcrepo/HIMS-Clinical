package com.hms.domain.inventory.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.*;
import org.hibernate.annotations.Filter;

@Entity @Table(name = "taxes") @Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class Tax extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 60) private String name;
    @Column(name = "tax_type", length = 30) private String taxType;
    @JsonProperty("rate")
    @Column(name = "total_rate", precision = 6, scale = 2) private BigDecimal totalRate = BigDecimal.ZERO;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "tax_id", nullable = false)
    private List<TaxCategory> categories = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void calculateTotalRate() {
        if (categories != null && !categories.isEmpty()) {
            this.totalRate = categories.stream()
                .map(TaxCategory::getRate)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            this.totalRate = BigDecimal.ZERO;
        }
    }
}
