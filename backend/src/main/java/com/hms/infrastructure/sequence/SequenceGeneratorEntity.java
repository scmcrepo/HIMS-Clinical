package com.hms.infrastructure.sequence;

import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.billing.model.SequenceResetPolicy;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

/**
 * Persistent sequence generator — one row per DocumentType.
 * The PESSIMISTIC_WRITE lock (SELECT FOR UPDATE) in the repository
 * prevents any two concurrent calls from receiving the same number.
 *
 * Reset policy:
 *   NEVER        → counter runs indefinitely
 *   FISCAL_YEAR  → resets to 1 on April 1 (Indian fiscal year)
 *   CALENDAR_YEAR → resets to 1 on January 1
 */
@Entity
@Table(name = "sequence_generators")
@Getter
@Setter
public class SequenceGeneratorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "prefix_string", nullable = false, length = 20)
    private String prefixString;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.ORDINAL)
    @Column(name = "reset_policy", nullable = false)
    private SequenceResetPolicy resetPolicy = SequenceResetPolicy.NEVER;

    @Column(name = "is_activated", nullable = false)
    private boolean activated = false;

    @Column(name = "current_counter", nullable = false)
    private long currentCounter = 1L;

    @Column(name = "current_fiscal_year")
    private Short currentFiscalYear;

    @Column(name = "activated_at")
    private LocalDate activatedAt;

    @Column(name = "deactivated_at")
    private LocalDate deactivatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /**
     * Core generation method — called inside a PESSIMISTIC_WRITE locked transaction.
     * Handles fiscal year / calendar year auto-reset.
     * Returns the formatted sequence string and increments the internal counter.
     */
    public String formatAndIncrement() {
        maybeResetCounter();
        String result = prefixString + String.format("%04d", currentCounter);
        this.currentCounter++;
        return result;
    }

    private void maybeResetCounter() {
        LocalDate today = LocalDate.now();
        if (resetPolicy == SequenceResetPolicy.FISCAL_YEAR) {
            // Indian fiscal year starts April 1
            int currentFY = today.getMonthValue() >= Month.APRIL.getValue()
                ? today.getYear()
                : today.getYear() - 1;
            short currentFYShort = (short) currentFY;
            if (currentFiscalYear == null || currentFiscalYear < currentFYShort) {
                this.currentCounter = 1L;
                this.currentFiscalYear = currentFYShort;
            }
        } else if (resetPolicy == SequenceResetPolicy.CALENDAR_YEAR) {
            short thisYear = (short) today.getYear();
            if (currentFiscalYear == null || currentFiscalYear < thisYear) {
                this.currentCounter = 1L;
                this.currentFiscalYear = thisYear;
            }
        }
    }

    public void activate() {
        this.activated = true;
        this.activatedAt = LocalDate.now();
        this.deactivatedAt = null;
    }

    public void deactivate() {
        this.activated = false;
        this.deactivatedAt = LocalDate.now();
    }
}
