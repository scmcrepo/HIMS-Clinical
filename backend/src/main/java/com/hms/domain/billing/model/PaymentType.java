package com.hms.domain.billing.model;

public enum PaymentType {
    DEPOSIT,        // 0 — advance collected on DRAFT bill only
    PAYMENT,        // 1 — payment collected on GENERATED bill only
    REFUND,         // 2 — refund after service cancellation
    ADVANCE_REFUND  // 3 — auto-created when overpayment detected on generateBill()
}
