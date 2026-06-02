package com.hms.domain.diagnostic.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.util.UUID;

/**
 * One test/study within a DiagnosticOrder.
 * Results are stored as plain text; structured result data goes in result_value.
 */
@Entity @Table(name = "diagnostic_order_lines")
@Getter @Setter @NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DiagnosticOrderLine {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false) private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false) private DiagnosticOrder order;

    @Column(name = "service_catalog_item_id", nullable = false) private UUID serviceCatalogItemId;
    @Column(name = "item_name", length = 200) private String itemName;
    @Column(name = "specimen_id") private UUID specimenId;
    @Column(name = "instruction", length = 255) private String instruction;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "payment_status", nullable = false) private DiagnosticPaymentStatus paymentStatus = DiagnosticPaymentStatus.ORDERED;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "test_status", nullable = false) private DiagnosticTestStatus testStatus = DiagnosticTestStatus.PENDING;

    @Column(name = "result_value", columnDefinition = "TEXT") private String resultValue;
    @Column(name = "result_unit", length = 50) private String resultUnit;
    @Column(name = "reference_range", length = 100) private String referenceRange;
    @Column(name = "result_recorded_at") private Instant resultRecordedAt;

    @CreatedBy  @Column(name = "created_by",  updatable = false) private UUID createdBy;
    @CreatedDate @Column(name = "created_at", updatable = false, nullable = false) private Instant createdAt;

    // ── Behaviour ──────────────────────────────────────────────────────────
    public void recordResult(String value, String unit, String referenceRange) {
        this.resultValue    = value;
        this.resultUnit     = unit;
        this.referenceRange = referenceRange;
        this.resultRecordedAt = Instant.now();
        this.testStatus = DiagnosticTestStatus.RESULTED;
    }

    public void cancel() { this.testStatus = DiagnosticTestStatus.CANCELLED; }
    public boolean hasResult() { return resultValue != null && !resultValue.isBlank(); }
}
