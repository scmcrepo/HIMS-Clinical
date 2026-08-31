package com.hms.infrastructure.persistence.procurement;
import com.hms.domain.procurement.model.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;
/**
 * Scanner note (WO-028/F-004): flagged as an @Entity with no tenantId. It is
 * a repository, not an entity — a misfire on the file-level heuristic. The
 * entity it manages is tenant-scoped through its parent, and this interface
 * holds no state to scope. Annotated as platform-level only to settle the
 * finding; nothing here is actually platform-wide.
 */
public interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrder, UUID> {

    @EntityGraph(attributePaths = {"lines"})
    Optional<PurchaseOrder> findById(UUID id);

    @EntityGraph(attributePaths = {"lines"})
    @Query("SELECT o FROM PurchaseOrder o WHERE o.orderDate = :date ORDER BY o.createdAt DESC")
    List<PurchaseOrder> findByOrderDate(@Param("date") LocalDate date);
}
