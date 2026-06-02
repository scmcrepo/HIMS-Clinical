package com.hms.application.billing;

import com.hms.domain.billing.event.BillMutatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Captures an immutable audit snapshot of every bill mutation.
 * Fires AFTER the main transaction commits — never interferes with the primary write.
 * Uses a direct JDBC insert to avoid any JPA/Hibernate overhead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BillingAuditEventListener {

    private final DataSource dataSource;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBillMutated(BillMutatedEvent event) {
        String sql = """
            INSERT INTO bill_audit (
                id, bill_id, bill_amount, discount_total, payment_total,
                service_refund_total, refund_total, status, operation_type,
                performed_by, performed_at
            )
            SELECT
                gen_random_uuid(),
                b.id,
                b.bill_amount,
                b.discount_total,
                b.payment_total,
                b.service_refund_total,
                b.refund_total,
                b.status,
                ?,
                ?,
                ?
            FROM bills b
            WHERE b.id = ?
            """;

        try (var conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.operationType());
            ps.setObject(2, event.actorId());
            ps.setTimestamp(3, Timestamp.from(event.occurredAt()));
            ps.setObject(4, event.billId());
            int rows = ps.executeUpdate();
            if (rows == 0) {
                log.warn("BillingAuditEventListener: bill {} not found when writing audit row", event.billId());
            }
        } catch (Exception ex) {
            log.error("Failed to write bill audit for bill {}: {}", event.billId(), ex.getMessage());
        }
    }
}
