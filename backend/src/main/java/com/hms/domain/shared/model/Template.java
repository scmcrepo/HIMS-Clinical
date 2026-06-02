package com.hms.domain.shared.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "template")
@Getter @Setter @NoArgsConstructor
public class Template extends AuditableEntity {

    @Column(name = "templatename", nullable = false)
    private String templateName;

    @Column(name = "template", columnDefinition = "TEXT")
    private String template;

    @Column(name = "templatedata", columnDefinition = "TEXT")
    private String templateData;

    @Column(name = "templatetype")
    @Enumerated(EnumType.ORDINAL)
    private CommonTemplate templateType;

    @Column(name = "sample_data", columnDefinition = "TEXT")
    private String sampleData;
}
