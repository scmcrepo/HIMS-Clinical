package com.hms.domain.casesheet.model;

import com.hms.domain.shared.model.AuditableEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores the filled-in field values for a single clinical encounter.
 * `data` JSONB: { fieldKey → value }
 * Partial-save supported via mergeData() — only supplied keys overwrite existing ones.
 * DB unique constraint on (encounter_id, template_id) prevents duplicates.
 */
@Entity
@Table(name = "case_sheet_records", indexes = {
    @Index(name = "idx_csr_encounter", columnList = "encounter_id"),
    @Index(name = "idx_csr_template",  columnList = "template_id")
})
@Getter @Setter @NoArgsConstructor
public class CaseSheetRecord extends AuditableEntity {

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private CaseSheetTemplate template;

    @Type(JsonBinaryType.class)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> data = new HashMap<>();

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    /** Merge (partial update) — incoming keys overwrite; absent keys are preserved */
    public void mergeData(Map<String, Object> incoming) {
        if (incoming != null) {
            if (this.data == null) this.data = new HashMap<>();
            this.data.putAll(incoming);
        }
    }
}
