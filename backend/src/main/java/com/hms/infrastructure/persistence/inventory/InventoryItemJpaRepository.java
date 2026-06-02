package com.hms.infrastructure.persistence.inventory;
import com.hms.domain.inventory.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
public interface InventoryItemJpaRepository extends JpaRepository<InventoryItem, UUID> {
    @Query("SELECT i FROM InventoryItem i WHERE i.status = 1 AND LOWER(i.name) LIKE LOWER(CONCAT('%',:q,'%'))")
    Page<InventoryItem> searchByName(@Param("q") String query, Pageable pageable);

    java.util.Optional<InventoryItem> findByName(String name);
}
