package com.hms.infrastructure.persistence.stockmovement;
import com.hms.domain.inventory.model.StockIndent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface StockIndentJpaRepository extends JpaRepository<StockIndent, UUID> {
    @Query("SELECT s FROM StockIndent s WHERE s.indentDate = :date ORDER BY s.createdAt DESC")
    List<StockIndent> findByDate(@Param("date") LocalDate date);
}
