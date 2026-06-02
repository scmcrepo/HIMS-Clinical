package com.hms.domain.shared.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "department_template")
@Getter @Setter @NoArgsConstructor
public class DepartmentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "template_id") // Matches V055 column name template_id
    private Template template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id") // Matches V055 column name department_id
    @JsonIgnore
    private Department department;
}
