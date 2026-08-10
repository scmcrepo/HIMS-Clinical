package com.hms.application.compliance;

import com.hms.infrastructure.persistence.compliance.PiiDisclosureAuditEntity;
import com.hms.infrastructure.persistence.compliance.PiiDisclosureAuditJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Records every release of unmasked personal data.
 *
 * <p>Writes run in {@code REQUIRES_NEW} so the audit row survives a rollback of
 * the business transaction. A disclosure attempt that failed and left no trace
 * is precisely the one an investigation later needs — and a denied attempt is
 * often more interesting than a successful one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PiiDisclosureAuditService {

    public static final String ABHA_CARD = "ABHA_CARD";
    public static final String EXTERNAL_HEALTH_RECORD = "EXTERNAL_HEALTH_RECORD";
    public static final String POLICY_DOCUMENT = "POLICY_DOCUMENT";

    private final PiiDisclosureAuditJpaRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String type, UUID subjectId, UUID resourceId,
                              UUID actorUserId, String purpose) {
        write(type, subjectId, resourceId, actorUserId, purpose, "SUCCESS", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String type, UUID subjectId, UUID resourceId,
                              UUID actorUserId, String purpose, String failureCode) {
        write(type, subjectId, resourceId, actorUserId, purpose, "FAILURE", failureCode);
    }

    /** A request that was refused. Worth its own outcome, not folded into failure. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenied(String type, UUID subjectId, UUID actorUserId, String reason) {
        write(type, subjectId, null, actorUserId, null, "DENIED", reason);
    }

    public List<PiiDisclosureAuditEntity> forSubject(UUID subjectId) {
        return repository.findBySubjectIdOrderByDisclosedAtDesc(subjectId);
    }

    public List<PiiDisclosureAuditEntity> byActor(UUID actorUserId) {
        return repository.findByActorUserIdOrderByDisclosedAtDesc(actorUserId);
    }

    private void write(String type, UUID subjectId, UUID resourceId, UUID actorUserId,
                       String purpose, String outcome, String failureCode) {
        try {
            PiiDisclosureAuditEntity row = new PiiDisclosureAuditEntity();
            row.setDisclosureType(type);
            row.setSubjectId(subjectId);
            row.setResourceId(resourceId);
            row.setActorUserId(actorUserId);
            row.setPurpose(purpose);
            row.setOutcome(outcome);
            row.setFailureCode(failureCode);
            row.setCorrelationId(MDC.get("correlationId"));
            repository.save(row);

            log.info("pii.disclosure type[{}] subjectId[{}] actor[{}] outcome[{}]",
                     type, subjectId, actorUserId, outcome);
        } catch (RuntimeException e) {
            // An audit failure must not swallow the caller's outcome, but it is
            // serious enough to be loud: this is the record a regulator asks for.
            log.error("pii.disclosure.audit_write_failed type[{}] subjectId[{}] cause[{}]",
                      type, subjectId, e.getClass().getSimpleName());
        }
    }
}
