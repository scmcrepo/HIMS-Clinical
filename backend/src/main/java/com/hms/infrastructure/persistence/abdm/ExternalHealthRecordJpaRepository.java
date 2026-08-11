package com.hms.infrastructure.persistence.abdm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExternalHealthRecordJpaRepository
        extends JpaRepository<ExternalHealthRecordEntity, UUID> {

    List<ExternalHealthRecordEntity> findByPatientIdOrderByRecordDateDesc(UUID patientId);

    /** "What did this consent let in?" — asked whenever one is revoked. */
    List<ExternalHealthRecordEntity> findByArtifactId(UUID artifactId);
}
