package com.hms.infrastructure.persistence.catalog;
import com.hms.domain.catalog.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;
public interface ServiceCategoryJpaRepository extends JpaRepository<ServiceCategory, UUID> {
    @Query("SELECT c FROM ServiceCategory c WHERE c.status = 1 ORDER BY c.name ASC")
    List<ServiceCategory> findAllActive();
    java.util.Optional<ServiceCategory> findByName(String name);

    @Query(value = "SELECT * FROM service_categories WHERE tenant_id = :tenantId AND LOWER(name) = LOWER(:name) AND status != 2 LIMIT 1", nativeQuery = true)
    java.util.Optional<ServiceCategory> findByNameAndTenantIdIgnoreCaseNative(@org.springframework.data.repository.query.Param("name") String name, @org.springframework.data.repository.query.Param("tenantId") UUID tenantId);
}
