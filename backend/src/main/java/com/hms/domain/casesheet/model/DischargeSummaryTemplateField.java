package com.hms.domain.casesheet.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hms.domain.shared.model.EntityStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "discharge_summary_template_fields", indexes = {
    @Index(name = "idx_dstf_template", columnList = "template_id"),
    @Index(name = "idx_dstf_order",    columnList = "template_id, display_order")
})
@Getter @Setter @NoArgsConstructor
public class DischargeSummaryTemplateField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    @JsonIgnore
    private DischargeSummaryTemplate template;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "field_type", nullable = false, length = 30)
    private String fieldType;

    @Column(name = "section", length = 80)
    private String section;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_required", nullable = false)
    private boolean required = false;

    @Column(name = "placeholder", length = 200)
    private String placeholder;

    @Column(name = "help_text", length = 300)
    private String helpText;

    @Type(JsonBinaryType.class)
    @Column(name = "options", columnDefinition = "jsonb")
    private List<Map<String, String>> options;

    @Type(JsonBinaryType.class)
    @Column(name = "validation", columnDefinition = "jsonb")
    private Map<String, Object> validation;

    @Column(name = "default_value", length = 200)
    private String defaultValue;

    @Column(name = "is_visible", nullable = false)
    private boolean visible = true;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "status", nullable = false)
    private EntityStatus status = EntityStatus.ACTIVE;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt = Instant.now();

    @PreUpdate
    public void onUpdate() { this.modifiedAt = Instant.now(); }
}
