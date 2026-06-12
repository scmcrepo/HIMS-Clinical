package com.hms.domain.casesheet.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "discharge_summary_templates", indexes = {
    @Index(name = "idx_dst_specialization", columnList = "specialization")
})
@Getter @Setter @NoArgsConstructor
public class DischargeSummaryTemplate extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "specialization", nullable = false, length = 60)
    private String specialization;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate = false;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<DischargeSummaryTemplateField> fields = new ArrayList<>();
}
