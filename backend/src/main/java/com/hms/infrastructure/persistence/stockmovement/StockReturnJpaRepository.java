package com.hms.infrastructure.persistence.stockmovement;
import com.hms.domain.inventory.model.StockReturn;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface StockReturnJpaRepository extends JpaRepository<StockReturn, UUID> {
    @Query("SELECT s FROM StockReturn s WHERE s.returnDate = :date ORDER BY s.createdAt DESC")
    List<StockReturn> findByDate(@Param("date") LocalDate date);
}
