package com.hms.domain.gst.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A validation error returned against a filing (A5).
 *
 * <p>Held line-item-wise, with the invoice number and field the GSP objected to, so the
 * person correcting it can go straight to the bill rather than reading a wall of JSON.
 */
@Entity
@Table(name = "gst_filing_errors")
@Getter @Setter @NoArgsConstructor
public class GstFilingError {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
