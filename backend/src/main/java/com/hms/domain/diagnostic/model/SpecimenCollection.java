package com.hms.domain.diagnostic.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a specimen collection event for a diagnostic order.
 * Generated when POST /diagnostics/recordSpecimenCollection is called.
 * Produces a PrefixType.SAMPLE sequence number.
 */
@Entity
@Table(name = "specimen_collections", indexes = {
    @Index(name = "idx_sc_diagnostic", columnList = "diagnostic_id")
})
@Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SpecimenCollection {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "diagnostic_id", nullable = false)
    private UUID diagnosticId;

    @Column(name = "specimen_id")
    private UUID specimenId;

    @Column(name = "order_line_id")
    private UUID orderLineId;

    @Column(name = "sample_number", length = 40)
    private String sampleNumber;

    @Column(name = "collection_notes", columnDefinition = "TEXT")
    private String collectionNotes;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt = Instant.now();

    @CreatedBy
    @Column(name = "collected_by", updatable = false)
    private UUID collectedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
