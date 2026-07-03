package com.hms.infrastructure.persistence.billing;
import com.hms.domain.billing.model.Payment;
import com.hms.domain.billing.model.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface PaymentJpaRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findAllByBill_Id(UUID billId);
    List<Payment> findAllByBill_IdAndPaymentType(UUID billId, com.hms.domain.billing.model.PaymentType type);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p WHERE (:from IS NULL OR p.paymentDate >= :from) AND (:to IS NULL OR p.paymentDate <= :to) ORDER BY p.recordedAt DESC")
    org.springframework.data.domain.Page<Payment> findByDateRange(@org.springframework.data.repository.query.Param("from") java.time.LocalDate from, @org.springframework.data.repository.query.Param("to") java.time.LocalDate to, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
        "WHERE p.tenantId = :tenantId " +
        "AND (:branchId IS NULL AND p.branchId IS NULL OR p.branchId = :branchId) " +
        "AND p.paymentDate = :paymentDate " +
        "AND p.paymentMode = :paymentMode " +
        "AND p.status = 'Active' " +
        "AND p.paymentType IN (com.hms.domain.billing.model.PaymentType.PAYMENT, com.hms.domain.billing.model.PaymentType.DEPOSIT)")
    long sumCollectionAmount(
        @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
        @org.springframework.data.repository.query.Param("branchId") UUID branchId,
        @org.springframework.data.repository.query.Param("paymentDate") java.time.LocalDate paymentDate,
        @org.springframework.data.repository.query.Param("paymentMode") com.hms.domain.billing.model.PaymentMode paymentMode
    );
}
