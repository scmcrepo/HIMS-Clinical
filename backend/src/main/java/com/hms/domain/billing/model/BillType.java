package com.hms.domain.billing.model;

/** Determines which PricingTier is selected for each charge line. */
public enum BillType {
    CASH,       // 0
    CREDIT,     // 1
    INSURANCE   // 2
}
