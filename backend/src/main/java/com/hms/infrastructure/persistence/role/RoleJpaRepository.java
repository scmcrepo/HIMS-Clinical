package com.hms.infrastructure.persistence.role;
import com.hms.infrastructure.persistence.shared.RoleEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {

    /**
     * All active roles across all tenants — used by the cache full-rebuild at startup.
     * (The Hibernate tenant @Filter is not active during ApplicationReadyEvent, so this
     * legitimately returns every tenant's roles; the cache groups them by tenantId.)
     */
    @Query("SELECT r FROM RoleEntity r LEFT JOIN FETCH r.features WHERE r.status = 1 ORDER BY r.name ASC")
    List<RoleEntity> findAllActiveWithFeatures();
    @Query("SELECT r FROM RoleEntity r LEFT JOIN FETCH r.features ORDER BY r.name ASC")
    List<RoleEntity> findAllWithFeatures();

    /** Active roles for a single tenant — used by per-tenant cache rebuilds. */
    @Query("""
        SELECT r FROM RoleEntity r LEFT JOIN FETCH r.features
        WHERE r.status = 1 AND r.tenantId = :tenantId
        ORDER BY r.name ASC
        """)
    List<RoleEntity> findAllActiveWithFeaturesByTenant(@Param("tenantId") UUID tenantId);

    /** Tenant-scoped name lookup (uniqueness is per-tenant now). */
    Optional<RoleEntity> findByNameAndTenantId(String name, UUID tenantId);

    Optional<RoleEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}

