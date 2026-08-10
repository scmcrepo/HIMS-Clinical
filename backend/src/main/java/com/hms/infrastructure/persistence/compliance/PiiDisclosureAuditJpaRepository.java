package com.hms.infrastructure.persistence.compliance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PiiDisclosureAuditJpaRepository
        extends JpaRepository<PiiDisclosureAuditEntity, UUID> {

    /** Answers a DPDP subject access request: everything disclosed about a person. */
    List<PiiDisclosureAuditEntity> findBySubjectIdOrderByDisclosedAtDesc(UUID subjectId);

    List<PiiDisclosureAuditEntity> findByActorUserIdOrderByDisclosedAtDesc(UUID actorUserId);
}
