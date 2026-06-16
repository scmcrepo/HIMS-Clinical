package com.hms.infrastructure.persistence.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface FeatureJpaRepository extends JpaRepository<FeatureEntity, UUID> {

    /**
     * DEPRECATED for tenant-scoped use. Feature keys are now unique only per-tenant
     * (uq_features_tenant_key), so a bare feature_key can match rows in several
     * tenants. Retained only for startup/global maintenance paths that already
     * iterate every tenant. Application code must use
     * {@link #findByFeatureKeyAndTenantId(String, UUID)} instead.
     */
    @Deprecated
    Optional<FeatureEntity> findByFeatureKey(String featureKey);

    /** Tenant-scoped lookup — the correct call for all per-tenant logic. */
    @Query("SELECT f FROM FeatureEntity f WHERE f.featureKey = :featureKey AND f.tenantId = :tenantId")
    Optional<FeatureEntity> findByFeatureKeyAndTenantId(@Param("featureKey") String featureKey,
                                                        @Param("tenantId") UUID tenantId);

    @Query("SELECT f FROM FeatureEntity f WHERE f.module = :module AND f.tenantId = :tenantId")
    List<FeatureEntity> findByModuleAndTenantId(@Param("module") String module,
                                                @Param("tenantId") UUID tenantId);

    @Query("SELECT f FROM FeatureEntity f WHERE f.tenantId = :tenantId")
    List<FeatureEntity> findAllByTenantId(@Param("tenantId") UUID tenantId);

    /** Retained for callers that still pass a module without a tenant (global/startup paths only). */
    @Query("SELECT f FROM FeatureEntity f WHERE f.module = :module")
    List<FeatureEntity> findByModule(@Param("module") String module);
}
