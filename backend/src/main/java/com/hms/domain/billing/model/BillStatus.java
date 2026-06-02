package com.hms.domain.billing.model;

/**
 * Bill status ordinals — stored as TINYINT in bills.status.
 *
 * CRITICAL — must match legacy BillStatus enum ordinals exactly:
 *   0 = DRAFT            (Draft)
 *   1 = SETTLED          (Settled — dueAmount <= 0 after generation)
 *   2 = WITH_DUE         (WithDue — dueAmount > 0 after generation)
 *   3 = REFUNDED         (Refunded — billAmount-discountTotal == serviceRefundTotal)
 *   4 = CANCELLED        (Cancelled)
 *
 * DO NOT reorder. These ordinals are stored in the DB.
 */
public enum BillStatus {
    DRAFT,      // 0
    SETTLED,    // 1
    WITH_DUE,   // 2  (previously PARTIALLY_SETTLED — renamed to match legacy WithDue)
    REFUNDED,   // 3
    CANCELLED   // 4
}
