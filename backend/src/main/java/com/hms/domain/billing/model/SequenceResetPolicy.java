package com.hms.domain.billing.model;

/**
 * Controls when a sequence generator resets its counter.
 * Ordinals stored in sequence_generators.reset_policy — DO NOT reorder.
 */
public enum SequenceResetPolicy {
    NEVER,        // 0 — counter never resets; runs indefinitely
    FISCAL_YEAR,  // 1 — resets on April 1 each year (Indian fiscal year)
    CALENDAR_YEAR // 2 — resets on January 1 each year
}
