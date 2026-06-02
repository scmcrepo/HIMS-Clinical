package com.hms.infrastructure.persistence.stock;
import com.hms.domain.inventory.model.TempStock;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface TempStockJpaRepository extends JpaRepository<TempStock, UUID> {
    @Query("SELECT t FROM TempStock t WHERE t.itemId = :itemId") List<TempStock> findByItemId(@Param("itemId") UUID itemId);
    @Query("SELECT SUM(t.quantity) FROM TempStock t WHERE t.itemId = :itemId AND (:batch IS NULL OR t.batchNumber = :batch)")
    Integer sumQuantity(@Param("itemId") UUID itemId, @Param("batch") String batch);
}
