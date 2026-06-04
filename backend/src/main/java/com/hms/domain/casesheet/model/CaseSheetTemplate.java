package com.hms.domain.casesheet.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A CaseSheetTemplate defines the FORM LAYOUT (ordered list of fields) for a specialization.
 * Templates are dynamically configurable — no code change needed to add new specializations.
 * The `specialization` field is a free-text tag matching the consultant's department name
 * (e.g. "ORTHOPAEDICS", "GENERAL", "PAEDIATRICS").
 */
@Entity
@Table(name = "case_sheet_templates", indexes = {
    @Index(name = "idx_cst_specialization", columnList = "specialization"),
    @Index(name = "idx_cst_visit_type",     columnList = "visit_type")
})
@Getter @Setter @NoArgsConstructor
public class CaseSheetTemplate extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "specialization", nullable = false, length = 60)
    private String specialization;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 10)
    private CaseSheetVisitType visitType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean defaultTemplate = false;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<CaseSheetTemplateField> fields = new ArrayList<>();
}
