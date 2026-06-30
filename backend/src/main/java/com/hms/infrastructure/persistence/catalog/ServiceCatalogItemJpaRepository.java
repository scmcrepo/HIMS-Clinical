
package com.hms.infrastructure.persistence.catalog;
import com.hms.domain.catalog.model.ServiceCatalogItem;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
public interface ServiceCatalogItemJpaRepository extends JpaRepository<ServiceCatalogItem, UUID> {
    @Query(value = "SELECT DISTINCT s FROM ServiceCatalogItem s LEFT JOIN FETCH s.pricingTiers WHERE s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) AND NOT EXISTS (SELECT c FROM com.hms.domain.charge.model.Charge c WHERE c.id = s.id AND c.status != com.hms.domain.shared.model.EntityStatus.ACTIVE)",
           countQuery = "SELECT COUNT(DISTINCT s) FROM ServiceCatalogItem s WHERE s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) AND NOT EXISTS (SELECT c FROM com.hms.domain.charge.model.Charge c WHERE c.id = s.id AND c.status != com.hms.domain.shared.model.EntityStatus.ACTIVE)")
    Page<ServiceCatalogItem> searchByName(@Param("q") String query, Pageable pageable);

    @Query(value = "SELECT DISTINCT s FROM ServiceCatalogItem s LEFT JOIN FETCH s.pricingTiers WHERE s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) AND s.categoryId NOT IN (SELECT cat.id FROM ServiceCategory cat WHERE LOWER(cat.name) = 'room charges') AND NOT EXISTS (SELECT c FROM com.hms.domain.charge.model.Charge c WHERE c.id = s.id AND c.status != com.hms.domain.shared.model.EntityStatus.ACTIVE)",
           countQuery = "SELECT COUNT(DISTINCT s) FROM ServiceCatalogItem s WHERE s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) AND s.categoryId NOT IN (SELECT cat.id FROM ServiceCategory cat WHERE LOWER(cat.name) = 'room charges') AND NOT EXISTS (SELECT c FROM com.hms.domain.charge.model.Charge c WHERE c.id = s.id AND c.status != com.hms.domain.shared.model.EntityStatus.ACTIVE)")
    Page<ServiceCatalogItem> searchByNameExcludingRoomCharges(@Param("q") String query, Pageable pageable);

    @Query(value = "SELECT DISTINCT s FROM ServiceCatalogItem s LEFT JOIN FETCH s.pricingTiers WHERE s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) AND s.categoryId IN (SELECT cat.id FROM ServiceCategory cat WHERE cat.categoryType IN (com.hms.domain.catalog.model.ServiceCategoryType.DIAGNOSTICS, com.hms.domain.catalog.model.ServiceCategoryType.CONSULTATION)) AND NOT EXISTS (SELECT c FROM com.hms.domain.charge.model.Charge c WHERE c.id = s.id AND c.status != com.hms.domain.shared.model.EntityStatus.ACTIVE)",
           countQuery = "SELECT COUNT(DISTINCT s) FROM ServiceCatalogItem s WHERE s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) AND s.categoryId IN (SELECT cat.id FROM ServiceCategory cat WHERE cat.categoryType IN (com.hms.domain.catalog.model.ServiceCategoryType.DIAGNOSTICS, com.hms.domain.catalog.model.ServiceCategoryType.CONSULTATION)) AND NOT EXISTS (SELECT c FROM com.hms.domain.charge.model.Charge c WHERE c.id = s.id AND c.status != com.hms.domain.shared.model.EntityStatus.ACTIVE)")
    Page<ServiceCatalogItem> searchByNameDiagnosticsAndConsultations(@Param("q") String query, Pageable pageable);

    @Query("SELECT DISTINCT s FROM ServiceCatalogItem s LEFT JOIN FETCH s.pricingTiers WHERE s.categoryId = :catId AND s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND NOT EXISTS (SELECT c FROM com.hms.domain.charge.model.Charge c WHERE c.id = s.id AND c.status != com.hms.domain.shared.model.EntityStatus.ACTIVE)")
    java.util.List<ServiceCatalogItem> findActiveByCategoryId(@Param("catId") UUID categoryId);

    @Query("SELECT s FROM ServiceCatalogItem s WHERE s.status = com.hms.domain.shared.model.EntityStatus.ACTIVE AND LOWER(s.name) = LOWER(:name) AND NOT EXISTS (SELECT c FROM com.hms.domain.charge.model.Charge c WHERE c.id = s.id AND c.status != com.hms.domain.shared.model.EntityStatus.ACTIVE)")
    java.util.List<ServiceCatalogItem> findActiveByNameIgnoreCase(@Param("name") String name);
}
