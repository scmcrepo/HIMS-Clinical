package com.hms.infrastructure.persistence.inventory;

import com.hms.domain.inventory.model.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitOfMeasureJpaRepository extends JpaRepository<UnitOfMeasure, UUID> {
    @Query("SELECT u FROM UnitOfMeasure u WHERE u.status = 1 ORDER BY u.name ASC")
    List<UnitOfMeasure> findAllActive();

    /**
     * Tenant-scoped lookup by name — uses a native query to bypass Hibernate @Filter issues
     * that arise inside PROPAGATION_REQUIRES_NEW transactions during bulk imports.
     */
    @Query(value = "SELECT * FROM units_of_measure WHERE tenant_id = :tenantId AND LOWER(name) = LOWER(:name) AND status = 1 LIMIT 1", nativeQuery = true)
    Optional<UnitOfMeasure> findByTenantIdAndNameIgnoreCase(@Param("tenantId") UUID tenantId, @Param("name") String name);

    /**
     * Tenant-scoped lookup by symbol — fallback when name doesn't match.
     */
    @Query(value = "SELECT * FROM units_of_measure WHERE tenant_id = :tenantId AND LOWER(symbol) = LOWER(:symbol) AND status = 1 LIMIT 1", nativeQuery = true)
    Optional<UnitOfMeasure> findByTenantIdAndSymbolIgnoreCase(@Param("tenantId") UUID tenantId, @Param("symbol") String symbol);
}
