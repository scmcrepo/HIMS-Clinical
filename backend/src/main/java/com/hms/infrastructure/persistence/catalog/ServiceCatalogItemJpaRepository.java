package com.hms.infrastructure.persistence.catalog;
import com.hms.domain.catalog.model.ServiceCatalogItem;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
public interface ServiceCatalogItemJpaRepository extends JpaRepository<ServiceCatalogItem, UUID> {
    @Query("SELECT s FROM ServiceCatalogItem s LEFT JOIN FETCH s.pricingTiers WHERE s.status < 2 AND LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%'))")
    Page<ServiceCatalogItem> searchByName(@Param("q") String query, Pageable pageable);
    @Query("SELECT s FROM ServiceCatalogItem s LEFT JOIN FETCH s.pricingTiers WHERE s.categoryId = :catId AND s.status = 1")
    java.util.List<ServiceCatalogItem> findActiveByCategoryId(@Param("catId") UUID categoryId);
}
