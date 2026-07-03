package com.hms.infrastructure.persistence.billing;

import com.hms.domain.billing.model.PettyCash;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface PettyCashJpaRepository extends JpaRepository<PettyCash, UUID> {

    @Query("SELECT pc FROM PettyCash pc WHERE " +
           "pc.paymentDate BETWEEN :from AND :to " +
           "ORDER BY pc.createdAt DESC")
    Page<PettyCash> findByFilters(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query("SELECT pc FROM PettyCash pc WHERE " +
           "pc.paymentDate BETWEEN :from AND :to AND " +
           "(LOWER(pc.givenTo) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(pc.sequenceNumber) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY pc.createdAt DESC")
    Page<PettyCash> findByFiltersWithSearch(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("query") String query,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(pc.amount), 0) FROM PettyCash pc " +
           "WHERE pc.tenantId = :tenantId " +
           "AND (:branchId IS NULL AND pc.branchId IS NULL OR pc.branchId = :branchId) " +
           "AND pc.paymentDate = :paymentDate " +
           "AND pc.paymentMode = :paymentMode " +
           "AND pc.status = 'Active'")
    long sumPettyCashAmount(
            @Param("tenantId") UUID tenantId,
            @Param("branchId") UUID branchId,
            @Param("paymentDate") LocalDate paymentDate,
            @Param("paymentMode") String paymentMode);
}
