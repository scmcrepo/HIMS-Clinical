package com.hms.infrastructure.persistence.procurement;
import com.hms.domain.procurement.model.PurchaseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface PurchaseReceiptJpaRepository extends JpaRepository<PurchaseReceipt, UUID> {
    @Query("SELECT r FROM PurchaseReceipt r WHERE r.receiptDate = :date ORDER BY r.createdAt DESC")
    List<PurchaseReceipt> findByReceiptDate(@Param("date") LocalDate date);
    @Query("SELECT r FROM PurchaseReceipt r WHERE r.departmentId = :deptId AND r.supplierId = :suppId ORDER BY r.receiptDate DESC")
    List<PurchaseReceipt> findBySupplierAndDepartment(@Param("suppId") UUID supplierId, @Param("deptId") UUID departmentId);
}
