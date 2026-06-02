package com.hms.infrastructure.persistence.stockmovement;
import com.hms.domain.inventory.model.StockIssue;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
public interface StockIssueJpaRepository extends JpaRepository<StockIssue, UUID> {
    @Query("SELECT s FROM StockIssue s WHERE s.issueDate = :date ORDER BY s.createdAt DESC")
    List<StockIssue> findByDate(@Param("date") LocalDate date);
}
