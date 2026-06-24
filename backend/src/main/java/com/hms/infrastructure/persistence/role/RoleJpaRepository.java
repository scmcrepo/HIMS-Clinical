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

    /** Tenant-scoped name lookup (fallback for imports). */
    @Query("SELECT r FROM RoleEntity r WHERE LOWER(r.name) = LOWER(:name) AND r.tenantId = :tenantId")
    Optional<RoleEntity> findByNameAndTenantId(@Param("name") String name, @Param("tenantId") UUID tenantId);

    /** Active roles for a single tenant and branch — used by per-tenant cache rebuilds and lists. */
    @Query("""
        SELECT r FROM RoleEntity r LEFT JOIN FETCH r.features
        WHERE r.status = 1 AND r.tenantId = :tenantId AND (r.branchId = :branchId OR r.branchId IS NULL)
        ORDER BY r.name ASC
        """)
    List<RoleEntity> findAllActiveWithFeaturesByTenantAndBranch(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId);

    @Query(value = "SELECT * FROM roles WHERE LOWER(name) = LOWER(:name) AND tenant_id = :tenantId AND (branch_id = :branchId OR branch_id IS NULL)", nativeQuery = true)
    Optional<RoleEntity> findByNameAndTenantIdAndBranchId(@Param("name") String name, @Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId);

    @Query("SELECT r FROM RoleEntity r WHERE r.id = :id AND r.tenantId = :tenantId AND (r.branchId = :branchId OR r.branchId IS NULL)")
    Optional<RoleEntity> findByIdAndTenantIdAndBranchId(@Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId);
}

