package com.hms.domain.billing.model;

/**
 * Every entity that gets a formatted sequence number has a DocumentType.
 * Ordinal values are stored in sequence_generators.document_type — DO NOT reorder.
 */
public enum DocumentType {
    BILL,             // 0
    RECEIPT,          // 1
    DEPOSIT,          // 2
    REFUND,           // 3
    LAB_ORDER,        // 4
    IP_ORDER,         // 5
    SAMPLE,           // 6
    PHARMACY_SALE,    // 7
    PAYMENT,          // 8
    PURCHASE_RECEIPT, // 9
    PURCHASE_RETURN,  // 10
    PURCHASE_ORDER,   // 11
    PATIENT,          // 12
    REPLENISHMENT,    // 13
    INVENTORY_ISSUE,  // 14
    CONSUMPTION,      // 15
    ADVANCE_REFUND,   // 16
    PURCHASE_REQUEST  // 17
}
