package com.hms.api.claims.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Confirm what the hospital's bank actually credited — Screen 5.3.
 *
 * <p>The amount is required even when it matches. Making the accountant type
 * the figure they read off the statement is the whole control; defaulting it to
 * the advised amount would turn reconciliation into a rubber stamp.
 */
public record ReconcileRequest(
    @NotNull(message = "bankCreditedPaise is required")
    @PositiveOrZero(message = "bank credited amount cannot be negative")
    Long bankCreditedPaise,

    String note
) {
}
