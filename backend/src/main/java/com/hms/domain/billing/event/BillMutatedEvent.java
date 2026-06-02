package com.hms.domain.billing.event;

import com.hms.domain.billing.model.BillStatus;
import com.hms.domain.shared.model.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after every BillingEngine mutation.
 * BillingAuditEventListener consumes this via @TransactionalEventListener(AFTER_COMMIT)
 * to write an immutable snapshot to the bill_audit table.
 */
public record BillMutatedEvent(
    UUID billId,
    BillStatus newStatus,
    UUID actorId,
    Instant occurredAt,
    String operationType
) implements DomainEvent {}
