package com.hms.infrastructure.persistence.item;

import com.hms.domain.inventory.model.InventoryItem;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ItemJpaRepository extends JpaRepository<InventoryItem, UUID> {

    @Query("SELECT i FROM InventoryItem i WHERE i.status = 1 AND LOWER(i.name) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY i.name")
    List<InventoryItem> searchByName(@Param("q") String q);

    @Query("SELECT DISTINCT i FROM InventoryItem i JOIN InventoryBatch b ON b.itemId = i.id WHERE i.status = 1 AND b.departmentId = :deptId AND LOWER(i.name) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY i.name")
    List<InventoryItem> searchByNameInDepartment(@Param("q") String q, @Param("deptId") UUID departmentId);

    @Query("SELECT i FROM InventoryItem i WHERE i.status = 1 ORDER BY i.name")
    Page<InventoryItem> findAllActivePaged(Pageable pageable);

    @Query("SELECT i FROM InventoryItem i WHERE i.status = 1 AND LOWER(i.name) LIKE LOWER(CONCAT('%',:q,'%')) ORDER BY i.name")
    Page<InventoryItem> searchPaged(@Param("q") String q, Pageable pageable);

    @Query("SELECT i FROM InventoryItem i WHERE i.status IN (0, 1) " +
           "AND (:q IS NULL OR :q = '' OR LOWER(i.name) LIKE LOWER(CONCAT('%',:q,'%'))) " +
           "AND (:catId IS NULL OR i.categoryId = :catId) " +
           "ORDER BY i.status DESC, i.name ASC")
    Page<InventoryItem> searchPagedWithCategory(@Param("q") String q, @Param("catId") UUID catId, Pageable pageable);

    @Query("SELECT i FROM InventoryItem i WHERE i.status = 1 ORDER BY i.name")
    List<InventoryItem> findAllActive();
}
