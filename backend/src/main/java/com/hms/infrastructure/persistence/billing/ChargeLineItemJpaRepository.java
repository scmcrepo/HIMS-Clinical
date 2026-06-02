package com.hms.infrastructure.persistence.billing;
import com.hms.domain.billing.model.ChargeLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
public interface ChargeLineItemJpaRepository extends JpaRepository<ChargeLineItem, UUID> {
    @Query("SELECT c FROM ChargeLineItem c WHERE c.bill.id = :billId AND c.status IS NULL")
    List<ChargeLineItem> findActiveByBillId(@Param("billId") UUID billId);
    @Query("SELECT c FROM ChargeLineItem c WHERE c.diagnosticOrderId = :orderId")
    List<ChargeLineItem> findByDiagnosticOrderId(@Param("orderId") UUID orderId);
}
