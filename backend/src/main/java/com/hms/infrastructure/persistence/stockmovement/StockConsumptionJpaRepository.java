package com.hms.infrastructure.persistence.stockmovement;
import com.hms.domain.inventory.model.StockConsumption;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface StockConsumptionJpaRepository extends JpaRepository<StockConsumption, UUID> {
    @Query("SELECT s FROM StockConsumption s WHERE s.consumptionDate = :date ORDER BY s.createdAt DESC")
    List<StockConsumption> findByDate(@Param("date") LocalDate date);
}
