package com.hms.infrastructure.persistence.smtp;

import com.hms.domain.smtp.model.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, UUID> {

    /** Find all active SMTP configurations (within the current tenant/branch scope). */
    List<SmtpConfig> findByActiveTrue();

    @Query("SELECT s FROM SmtpConfig s WHERE s.tenantId = :tenantId AND s.branchId = :branchId AND s.active = true")
    List<SmtpConfig> findActiveByTenantAndBranch(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId);

    @Query("SELECT s FROM SmtpConfig s WHERE s.tenantId = :tenantId AND s.branchId IS NULL AND s.active = true")
    List<SmtpConfig> findActiveByTenantOnly(@Param("tenantId") UUID tenantId);
}
