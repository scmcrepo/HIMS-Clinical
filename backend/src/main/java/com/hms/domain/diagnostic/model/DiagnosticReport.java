package com.hms.domain.diagnostic.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "diagnostic_reports", indexes = {
    @Index(name = "idx_dr_order_line", columnList = "diagnostic_order_line_id"),
    @Index(name = "idx_dr_template", columnList = "diagnostic_template_id")
})
@Getter @Setter @NoArgsConstructor
public class DiagnosticReport extends AuditableEntity {

    @Column(name = "diagnostic_order_line_id", nullable = false)
    private UUID diagnosticOrderLineId;

    @Column(name = "diagnostic_template_id")
    private UUID diagnosticTemplateId;

    @Column(name = "lab_template_detail_id")
    private UUID labTemplateDetailId;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    @Column(name = "result", length = 30)
    private String result;

    @Column(name = "is_approved")
    private Boolean isApproved = false;

    @Column(name = "template_data", columnDefinition = "TEXT")
    private String templateData;
}
