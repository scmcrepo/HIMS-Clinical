package com.hms.infrastructure.persistence.inventory;
import com.hms.domain.inventory.model.InventoryBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface InventoryBatchJpaRepository extends JpaRepository<InventoryBatch, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBatch b WHERE b.id = :id")
    Optional<InventoryBatch> findByIdForUpdate(@Param("id") UUID id);
    @Query("SELECT b FROM InventoryBatch b WHERE b.itemId = :itemId AND b.departmentId = :deptId AND b.currentQuantity > 0 ORDER BY b.expiryDate ASC NULLS LAST")
    List<InventoryBatch> findAvailableByItemAndDept(@Param("itemId") UUID itemId, @Param("deptId") UUID deptId);
    @Query("SELECT b FROM InventoryBatch b WHERE b.departmentId = :deptId AND b.expiryDate < :date AND b.currentQuantity > 0")
    List<InventoryBatch> findExpiredBatchesInDept(@Param("deptId") UUID deptId, @Param("date") LocalDate date);
}
