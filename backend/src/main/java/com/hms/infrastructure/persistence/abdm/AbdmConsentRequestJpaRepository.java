package com.hms.infrastructure.persistence.abdm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbdmConsentRequestJpaRepository
        extends JpaRepository<AbdmConsentRequestEntity, UUID> {

    List<AbdmConsentRequestEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    Optional<AbdmConsentRequestEntity> findByCorrelationId(String correlationId);

    Optional<AbdmConsentRequestEntity> findByConsentRequestId(String consentRequestId);
}
