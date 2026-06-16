package com.hms.infrastructure.persistence.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlug(String slug);

    List<TenantEntity> findAllByStatus(short status);

    boolean existsBySlug(String slug);
}
