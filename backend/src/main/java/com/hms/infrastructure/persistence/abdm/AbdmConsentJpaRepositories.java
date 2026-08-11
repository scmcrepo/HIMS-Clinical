package com.hms.infrastructure.persistence.abdm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Grouped so the three Module 3 repositories stay readable together. */
public final class AbdmConsentJpaRepositories {

    private AbdmConsentJpaRepositories() {
    }

    public interface Requests extends JpaRepository<AbdmConsentRequestEntity, UUID> {
        List<AbdmConsentRequestEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

        Optional<AbdmConsentRequestEntity> findByCorrelationId(String correlationId);

        Optional<AbdmConsentRequestEntity> findByConsentRequestId(String consentRequestId);
    }

    public interface Artifacts extends JpaRepository<AbdmConsentArtifactEntity, UUID> {
        List<AbdmConsentArtifactEntity> findByPatientIdOrderByExpiresAtDesc(UUID patientId);

        Optional<AbdmConsentArtifactEntity> findByArtifactId(String artifactId);

        List<AbdmConsentArtifactEntity> findByConsentRequestId(UUID consentRequestId);
    }

    public interface Records extends JpaRepository<ExternalHealthRecordEntity, UUID> {
        List<ExternalHealthRecordEntity> findByPatientIdOrderByRecordDateDesc(UUID patientId);

        /** "What did this consent let in?" — asked whenever one is revoked. */
        List<ExternalHealthRecordEntity> findByArtifactId(UUID artifactId);
    }
}
