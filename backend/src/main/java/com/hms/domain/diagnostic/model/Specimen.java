package com.hms.domain.diagnostic.model;
import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "specimens") @Getter @Setter @NoArgsConstructor
public class Specimen extends AuditableEntity {
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "description", columnDefinition = "TEXT") private String description;
}
