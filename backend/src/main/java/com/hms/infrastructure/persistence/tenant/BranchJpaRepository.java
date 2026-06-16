package com.hms.infrastructure.persistence.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchJpaRepository extends JpaRepository<BranchEntity, UUID> {

    List<BranchEntity> findAllByTenantId(UUID tenantId);

    List<BranchEntity> findAllByTenantIdAndStatus(UUID tenantId, short status);

    Optional<BranchEntity> findByTenantIdAndIsDefaultTrue(UUID tenantId);

    Optional<BranchEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
