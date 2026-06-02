package com.hms.domain.inventory.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "units_of_measure")
@Getter
@Setter
@NoArgsConstructor
public class UnitOfMeasure extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "symbol", length = 10)
    private String symbol;
}
