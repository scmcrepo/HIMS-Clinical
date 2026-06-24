package com.hms.infrastructure.persistence.smtp;

import com.hms.domain.smtp.model.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, UUID> {

    /** Find all active SMTP configurations (within the current tenant/branch scope). */
    List<SmtpConfig> findByActiveTrue();
}
