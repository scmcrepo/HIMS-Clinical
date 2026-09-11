package com.hms.domain.gst.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One invoice line as it was reported, frozen at generation time.
 *
 * <p>This is a snapshot, not a view. Reading these figures back out of the live billing
 * tables months later would give whatever those tables say <em>now</em> — after
 * cancellations, refunds and price corrections — which is not what was filed.
 *
 * <p>Deliberately not an {@code AuditableEntity}: a line item is immutable once written
 * and carries no lifecycle of its own, so the status and modified-by columns that base
 * class brings would only ever hold defaults.
 */
@Entity
@Table(name = "gst_filing_line_items")
@Getter @Setter @NoArgsConstructor
public class GstFilingLineItem {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** PHARMACY or SERVICE — which side of the business the supply came from. */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "recipient_gstin", length = 512)
    private String recipientGstin;

    @Column(name = "place_of_supply", length = 2)
    private String placeOfSupply;

    @Column(name = "hsn_sac_code", length = 20)
    private String hsnSacCode;

    @Column(name = "tax_rate", precision = 6, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "taxable_value", precision = 16, scale = 2)
    private BigDecimal taxableValue = BigDecimal.ZERO;

    @Column(name = "cgst", precision = 16, scale = 2)
    private BigDecimal cgst = BigDecimal.ZERO;

    @Column(name = "sgst", precision = 16, scale = 2)
    private BigDecimal sgst = BigDecimal.ZERO;

    @Column(name = "igst", precision = 16, scale = 2)
    private BigDecimal igst = BigDecimal.ZERO;

    @Column(name = "cess", precision = 16, scale = 2)
    private BigDecimal cess = BigDecimal.ZERO;

    @Column(name = "reverse_charge", nullable = false)
    private boolean reverseCharge = false;

    /** For services: TAXABLE / EXEMPT / NIL_RATED / NON_GST / UNCLASSIFIED. */
    @Column(name = "gst_treatment", length = 20)
    private String gstTreatment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
