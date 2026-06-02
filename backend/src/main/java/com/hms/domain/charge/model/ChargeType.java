package com.hms.domain.charge.model;
/** Ordinals stored in DB — DO NOT reorder. */
public enum ChargeType {
    CHARGE,   // 0 — standard charge
    PACKAGE,  // 1 — IP package (absorbs other charges up to ceiling)
    IP        // 2 — inpatient-only charge
}
