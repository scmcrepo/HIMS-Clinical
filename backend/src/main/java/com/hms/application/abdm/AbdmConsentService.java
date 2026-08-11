package com.hms.application.abdm;

import com.hms.application.compliance.PiiDisclosureAuditService;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.abdm.AbdmConsentArtifactEntity;
import com.hms.infrastructure.persistence.abdm.AbdmConsentArtifactJpaRepository;
import com.hms.infrastructure.persistence.abdm.AbdmConsentRequestJpaRepository;
import com.hms.infrastructure.persistence.abdm.ExternalHealthRecordJpaRepository;
import com.hms.infrastructure.persistence.abdm.AbdmConsentRequestEntity;
import com.hms.infrastructure.persistence.abdm.ExternalHealthRecordEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ABDM Health Information User flow — Module 3.
 *
 * <p>The hospital asking the patient, through ABDM's Consent Manager, for
 * permission to read records held by other providers.
 *
 * <h2>This is not the DPDP consent register</h2>
 * {@code ConsentService} records the hospital's own lawful basis for processing
 * data it holds. An artifact here is issued and signed by the Consent Manager,
 * scoped to specific record types and a date range, and it expires. A DPDP
 * consent is never authority to pull another provider's records, and an expired
 * artifact is never standing permission. The two must not be substituted.
 *
 * <h2>Every read is gated and audited</h2>
 * {@link #recordsFor} re-evaluates the artifact against the clock on every call
 * rather than trusting the stored state, because an artifact stored as GRANTED
 * goes stale on its own when its expiry passes and nothing writes to the row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbdmConsentService {

    private final AbdmConsentClient client;
    private final AbdmConsentRequestJpaRepository requests;
    private final AbdmConsentArtifactJpaRepository artifacts;
    private final ExternalHealthRecordJpaRepository records;
    private final PiiDisclosureAuditService disclosureAudit;
    private final MeterRegistry meters;

    /**
     * Ask the patient for consent — Screen 3.1.
     *
     * <p>Validates the purpose, record types and date range locally first. The
     * Consent Manager would accept a nonsensical request and forward it to the
     * patient, who would then be asked to approve something that can return
     * nothing. That wastes the one interaction the hospital gets.
     */
    @Transactional
    public AbdmConsentRequestEntity requestConsent(UUID patientId, UUID encounterId,
                                                   String purposeCode, Set<String> hiTypes,
                                                   LocalDate from, LocalDate to,
                                                   Instant expiresAt, UUID requestedBy) {

        if (!ConsentArtifactRules.isValidPurpose(purposeCode)) {
            throw new BusinessRuleViolationException("Unknown ABDM purpose of request");
        }
        if (!ConsentArtifactRules.areValidHiTypes(hiTypes)) {
            throw new BusinessRuleViolationException("Select at least one valid record type");
        }
        if (!ConsentArtifactRules.isValidRange(from, to, LocalDate.now())) {
            throw new BusinessRuleViolationException(
                "The date range must start on or before today and end on or after it starts");
        }
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            // An artifact without a future expiry would authorise nothing, and
            // permitsFetch deliberately treats a missing expiry as invalid.
            throw new BusinessRuleViolationException(
                "Set when this consent should expire");
        }

        String correlationId = UUID.randomUUID().toString();

        AbdmConsentRequestEntity request = new AbdmConsentRequestEntity();
        request.setPatientId(patientId);
        request.setEncounterId(encounterId);
        request.setCorrelationId(correlationId);
        request.setPurposeCode(purposeCode);
        request.setHiTypes(String.join(",", new LinkedHashSet<>(hiTypes)));
        request.setDateRangeFrom(from);
        request.setDateRangeTo(to);
        request.setExpiresAt(expiresAt);
        request.setRequestedBy(requestedBy);
        request.setRequestState("REQUESTED");

        // Persisted before the call so a fast Consent Manager callback has a row
        // to match against.
        AbdmConsentRequestEntity saved = requests.save(request);

        try {
            String cmRequestId = client.initConsentRequest(
                patientId, purposeCode, hiTypes, from, to, expiresAt, correlationId);
            saved.setConsentRequestId(cmRequestId);
            saved.setRequestState("PENDING_APPROVAL");
        } catch (RuntimeException e) {
            saved.setRequestState("DENIED");
            saved.setFailureCode(e.getClass().getSimpleName());
            requests.save(saved);
            counter("request_failed").increment();
            throw e;
        }

        counter("requested").increment();
        log.info("abdm.consent.requested patientId[{}] correlationId[{}] purpose[{}] types[{}]",
                 patientId, correlationId, purposeCode, saved.getHiTypes());
        return requests.save(saved);
    }

    /**
     * Record an artifact the Consent Manager granted.
     *
     * <p>Idempotent on artifact id: the CM may notify more than once, and a
     * duplicate artifact would make one grant look like several.
     */
    @Transactional
    public AbdmConsentArtifactEntity recordGrant(String consentRequestId, String artifactId,
                                                 String signature, String hipId, String hipName,
                                                 Instant grantedAt, Instant expiresAt) {

        AbdmConsentRequestEntity request = requests.findByConsentRequestId(consentRequestId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No consent request for " + consentRequestId));

        var existing = artifacts.findByArtifactId(artifactId);
        if (existing.isPresent()) {
            log.info("abdm.consent.artifact.duplicate artifactId[{}] ignored", artifactId);
            return existing.get();
        }

        AbdmConsentArtifactEntity artifact = new AbdmConsentArtifactEntity();
        artifact.setConsentRequestId(request.getId());
        artifact.setPatientId(request.getPatientId());
        artifact.setArtifactId(artifactId);
        artifact.setSignature(signature);
        artifact.setHipId(hipId);
        artifact.setHipName(hipName);
        artifact.setGrantedAt(grantedAt);
        artifact.setExpiresAt(expiresAt);
        artifact.setArtifactState(ConsentArtifactRules.GRANTED);

        request.setRequestState("GRANTED");
        requests.save(request);

        counter("granted").increment();
        log.info("abdm.consent.granted patientId[{}] artifactId[{}] hip[{}]",
                 request.getPatientId(), artifactId, hipId);
        return artifacts.save(artifact);
    }

    /** The patient denied the request. Terminal. */
    @Transactional
    public void recordDenial(String consentRequestId) {
        requests.findByConsentRequestId(consentRequestId).ifPresent(r -> {
            r.setRequestState("DENIED");
            requests.save(r);
            counter("denied").increment();
            log.info("abdm.consent.denied patientId[{}]", r.getPatientId());
        });
    }

    /**
     * The patient withdrew consent.
     *
     * <p>Records what the artifact had already admitted, because that is the
     * question asked immediately afterwards and it cannot be reconstructed once
     * anyone starts deleting.
     */
    @Transactional
    public void recordRevocation(String artifactId) {
        AbdmConsentArtifactEntity artifact = artifacts.findByArtifactId(artifactId)
            .orElseThrow(() -> new ResourceNotFoundException("Consent artifact " + artifactId));

        artifact.setRevokedAt(Instant.now());
        artifact.setArtifactState(ConsentArtifactRules.REVOKED);
        artifacts.save(artifact);

        int admitted = records.findByArtifactId(artifact.getId()).size();
        counter("revoked").increment();
        log.warn("abdm.consent.revoked artifactId[{}] patientId[{}] recordsAlreadyFetched[{}]",
                 artifactId, artifact.getPatientId(), admitted);
    }

    /**
     * Fetch records under an artifact.
     *
     * <p>Refuses unless the artifact permits it <em>right now</em>. Anything
     * outside the consented types or date range is dropped rather than stored:
     * a HIP that over-shares is not authority to keep what it sent.
     */
    @Transactional
    public List<ExternalHealthRecordEntity> fetchRecords(UUID artifactRowId, UUID actorUserId) {
        AbdmConsentArtifactEntity artifact = artifacts.findById(artifactRowId)
            .orElseThrow(() -> new ResourceNotFoundException("Consent artifact", artifactRowId));

        Instant now = Instant.now();
        if (!ConsentArtifactRules.permitsFetch(artifact.getArtifactState(),
                                               artifact.getExpiresAt(),
                                               artifact.getRevokedAt(), now)) {
            disclosureAudit.recordDenied(PiiDisclosureAuditService.EXTERNAL_HEALTH_RECORD,
                                         artifact.getPatientId(), actorUserId,
                                         ConsentArtifactRules.effectiveState(
                                             artifact.getArtifactState(), artifact.getExpiresAt(),
                                             artifact.getRevokedAt(), now));
            throw new BusinessRuleViolationException(
                "This consent is no longer valid. Request consent again.");
        }

        AbdmConsentRequestEntity request = requests.findById(artifact.getConsentRequestId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Consent request", artifact.getConsentRequestId()));

        Set<String> consentedTypes = splitTypes(request.getHiTypes());

        List<AbdmConsentClient.ExternalRecord> fetched =
            client.fetchRecords(artifact.getArtifactId(), artifact.getSignature());

        List<ExternalHealthRecordEntity> kept = fetched.stream()
            .filter(r -> ConsentArtifactRules.coversHiType(consentedTypes, r.hiType()))
            .filter(r -> ConsentArtifactRules.withinConsentedRange(
                r.recordDate() == null ? null
                    : r.recordDate().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                request.getDateRangeFrom(), request.getDateRangeTo()))
            .map(r -> {
                ExternalHealthRecordEntity e = new ExternalHealthRecordEntity();
                e.setPatientId(artifact.getPatientId());
                e.setArtifactId(artifact.getId());
                e.setHiType(r.hiType());
                e.setRecordDate(r.recordDate());
                e.setSourceHipId(artifact.getHipId());
                e.setSourceHipName(artifact.getHipName());
                e.setPayload(r.payload());
                e.setDisplayTitle(r.displayTitle());
                e.setFetchedAt(Instant.now());
                return records.save(e);
            })
            .collect(Collectors.toList());

        int dropped = fetched.size() - kept.size();
        if (dropped > 0) {
            // Worth a warning: it means a HIP sent more than the patient allowed.
            log.warn("abdm.records.out_of_scope_dropped artifactId[{}] dropped[{}]",
                     artifact.getArtifactId(), dropped);
            counter("out_of_scope_dropped").increment(dropped);
        }

        disclosureAudit.recordSuccess(PiiDisclosureAuditService.EXTERNAL_HEALTH_RECORD,
                                      artifact.getPatientId(), artifact.getId(), actorUserId,
                                      "Fetched " + kept.size() + " records under ABDM consent");
        counter("records_fetched").increment(kept.size());
        return kept;
    }

    /**
     * Records visible to a clinician — Screen 3.2.
     *
     * <p>Filtered against live artifact validity, not the stored state. Records
     * fetched under a consent that has since expired or been revoked stop being
     * viewable, which is the whole point of a time-boxed grant.
     */
    public List<ExternalHealthRecordEntity> recordsFor(UUID patientId) {
        Instant now = Instant.now();
        Set<UUID> live = artifacts.findByPatientIdOrderByExpiresAtDesc(patientId).stream()
            .filter(a -> ConsentArtifactRules.permitsFetch(a.getArtifactState(), a.getExpiresAt(),
                                                           a.getRevokedAt(), now))
            .map(AbdmConsentArtifactEntity::getId)
            .collect(Collectors.toSet());

        return records.findByPatientIdOrderByRecordDateDesc(patientId).stream()
            .filter(r -> live.contains(r.getArtifactId()))
            .collect(Collectors.toList());
    }

    /** Open one record. Audited separately: this is when PHI is actually read. */
    @Transactional
    public ExternalHealthRecordEntity openRecord(UUID recordId, UUID actorUserId) {
        ExternalHealthRecordEntity record = records.findById(recordId)
            .orElseThrow(() -> new ResourceNotFoundException("External record", recordId));

        AbdmConsentArtifactEntity artifact = artifacts.findById(record.getArtifactId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Consent artifact", record.getArtifactId()));

        Instant now = Instant.now();
        if (!ConsentArtifactRules.permitsFetch(artifact.getArtifactState(), artifact.getExpiresAt(),
                                               artifact.getRevokedAt(), now)) {
            disclosureAudit.recordDenied(PiiDisclosureAuditService.EXTERNAL_HEALTH_RECORD,
                                         record.getPatientId(), actorUserId, "CONSENT_NOT_VALID");
            throw new BusinessRuleViolationException("The consent covering this record has ended");
        }

        disclosureAudit.recordSuccess(PiiDisclosureAuditService.EXTERNAL_HEALTH_RECORD,
                                      record.getPatientId(), record.getId(), actorUserId,
                                      "Viewed external " + record.getHiType());
        return record;
    }

    /**
     * Copy an external record into the local case sheet.
     *
     * <p>Marks the source rather than moving it. The imported copy becomes the
     * hospital's own record with its own provenance, while the original stays
     * tied to the artifact that authorised it — so importing cannot be used to
     * outlive the consent.
     */
    @Transactional
    public ExternalHealthRecordEntity markImported(UUID recordId, UUID caseSheetId, UUID actor) {
        ExternalHealthRecordEntity record = records.findById(recordId)
            .orElseThrow(() -> new ResourceNotFoundException("External record", recordId));

        if (record.getImportedAt() != null) {
            throw new BusinessRuleViolationException("This record has already been imported");
        }

        record.setImportedAt(Instant.now());
        record.setImportedBy(actor);
        record.setImportedCaseSheetId(caseSheetId);

        counter("imported").increment();
        log.info("abdm.records.imported recordId[{}] caseSheetId[{}]", recordId, caseSheetId);
        return records.save(record);
    }

    public List<AbdmConsentRequestEntity> requestsFor(UUID patientId) {
        return requests.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    public List<AbdmConsentArtifactEntity> artifactsFor(UUID patientId) {
        return artifacts.findByPatientIdOrderByExpiresAtDesc(patientId);
    }

    static Set<String> splitTypes(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Counter counter(String event) {
        return Counter.builder("hms.abdm.consent.events").tag("event", event).register(meters);
    }
}
