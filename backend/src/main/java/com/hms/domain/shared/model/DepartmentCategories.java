package com.hms.domain.shared.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "department_categories")
@Getter @Setter @NoArgsConstructor
public class DepartmentCategories {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id") // SCMC has name="category" but wait, the Flyway migration has category_id. Let's make sure it matches V055 where we created category_id.
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id") // SCMC has department but Flyway migration has department_id. Let's match V055.
    @JsonIgnore
    private Department department;
}
