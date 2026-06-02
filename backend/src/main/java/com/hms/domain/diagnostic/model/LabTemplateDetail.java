package com.hms.domain.diagnostic.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lab_template_details")
@Getter @Setter @NoArgsConstructor
public class LabTemplateDetail extends AuditableEntity {
    @Column(name = "result_name", nullable = false, length = 200)
    private String resultName;

    @Column(name = "normal_range", length = 500)
    private String normalRange;

    @Column(name = "normal_range_exp", columnDefinition = "TEXT")
    private String normalRangeExp;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "lab_type", length = 30)
    private String labType = "NUMERIC";

    @Column(name = "order_number")
    private Integer orderNumber = 0;

    @Column(name = "row_count")
    private Short rowCount = 1;
}
