package com.hms.domain.diagnostic.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.domain.shared.model.Department;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;

/**
 * Diagnostic template — defines test structure, specimen, department, and lab parameters.
 * format: LAB_TEMPLATE (structured results) or CUSTOM_TEMPLATE (free-form radiology).
 */
@Entity
@Table(name = "diagnostic_templates", indexes = {
    @Index(name = "idx_dt_charge", columnList = "charge_id")
})
@Getter @Setter @NoArgsConstructor
public class DiagnosticTemplate extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "diagnostic_type", nullable = false)
    private DiagnosticType diagnosticType = DiagnosticType.LAB;

    @Column(name = "format", length = 30)
    private String format = "LAB_TEMPLATE";

    @Column(name = "charge_id")
    private UUID chargeId;

    @Column(name = "specimen_id")
    private UUID specimenId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "order_number")
    private Integer orderNumber = 0;

    @Column(name = "header", length = 200)
    private String header;

    @Column(name = "method", length = 200)
    private String method;

    @Column(name = "reference_range", length = 200)
    private String referenceRange;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "lab_template_type", length = 30)
    private String labTemplateType;

    @Column(name = "template_html", columnDefinition = "TEXT")
    private String templateHtml;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinTable(name = "diagnostic_template_lab_template",
        joinColumns = @JoinColumn(name = "diagnostic_template_id"),
        inverseJoinColumns = @JoinColumn(name = "lab_template_detail_id"))
    private Set<LabTemplateDetail> labTemplateDetails = new LinkedHashSet<>();

    // Transient helper for specimen name
    @Transient
    private String specimenName;

    @Transient
    private String chargeName;
}
