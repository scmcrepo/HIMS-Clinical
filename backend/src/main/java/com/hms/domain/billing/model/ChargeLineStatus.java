package com.hms.domain.billing.model;

/**
 * Status of a ChargeLineItem.
 *
 * CRITICAL — ordinals stored as TINYINT in bill_details.status:
 *   null   = active (NOT stored as 0 — stored as SQL NULL)
 *   1      = CANCELLED   (Hibernate @Enumerated(ORDINAL) ordinal 0)
 *   2      = MODIFIED    (ordinal 1)
 *   3      = REFUNDED    (ordinal 2)
 *
 * The "null=active" pattern is implemented by storing NULL in the DB column
 * and the Hibernate 'activeService' filter: (status IS NULL OR status != 1).
 *
 * DO NOT reorder — ordinals map to legacy ServiceStatus values.
 */
public enum ChargeLineStatus {
    CANCELLED,   // ordinal 0 → stored as 1 in DB (Hibernate ordinal + 1 via @Column definition)
    MODIFIED,    // ordinal 1 → stored as 2
    REFUNDED     // ordinal 2 → stored as 3
}
