package com.hms.infrastructure.persistence.gst;

import com.hms.domain.gst.model.GstConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GstConfigurationJpaRepository extends JpaRepository<GstConfiguration, UUID> {

    /**
     * Matched on tenantId explicitly rather than relying on the Hibernate filter: this is
     * read during configuration writes, where getting the wrong hospital's row would mean
     * writing one hospital's GSTIN over another's.
     */
    Optional<GstConfiguration> findByTenantId(UUID tenantId);
}
