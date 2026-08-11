package com.hms.infrastructure.persistence.abdm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbdmConsentArtifactJpaRepository
        extends JpaRepository<AbdmConsentArtifactEntity, UUID> {

    List<AbdmConsentArtifactEntity> findByPatientIdOrderByExpiresAtDesc(UUID patientId);

    Optional<AbdmConsentArtifactEntity> findByArtifactId(String artifactId);

    List<AbdmConsentArtifactEntity> findByConsentRequestId(UUID consentRequestId);
}
