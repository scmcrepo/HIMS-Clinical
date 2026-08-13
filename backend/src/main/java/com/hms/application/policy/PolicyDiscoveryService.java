package com.hms.application.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.hms.application.compliance.ConsentPurpose;
import com.hms.application.compliance.ConsentService;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.nhcx.NhcxClient;
import com.hms.infrastructure.persistence.policy.DiscoveredPolicyEntity;
import com.hms.infrastructure.persistence.policy.DiscoveredPolicyJpaRepository;
import com.hms.infrastructure.persistence.policy.PolicyCoverageEntity;
import com.hms.infrastructure.persistence.policy.PolicyCoverageJpaRepository;
import com.hms.infrastructure.persistence.policy.PolicyExclusionEntity;
import com.hms.infrastructure.persistence.policy.PolicyExclusionJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Policy discovery and coverage verification — Screens 1.2 and 2.1.
 *
 * <h2>Why discovery needs the patient's OTP</h2>
 * A discovery request asks the national registry which insurers a named person
 * holds policies with. That is a disclosure about the patient's financial
 * affairs, and the hospital has no standing to obtain it merely because the
 * person walked in. The OTP is the patient authorising it, and this service will
 * not call the registry without one — see {@link #confirmDiscovery}.
 *
 * <h2>Why discovery results are not insurance rows</h2>
 * What comes back is the payer's assertion. It becomes a policy the hospital
 * will bill against only when a human links it, at which point
 * {@link DiscoveredPolicyEntity#getLinkedInsuranceId()} records who and when.
 *
 * <p>Everything here is asynchronous: NHCX acknowledges, and the real answer
 * arrives later on the callback. Methods that submit return the correlation id
 * so the caller can poll or await the callback; they never pretend to return an
 * answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyDiscoveryService {

    private final NhcxClient nhcx;
    private final CoverageResponseParser parser;
    private final DiscoveredPolicyJpaRepository discovered;
    private final PolicyCoverageJpaRepository coverages;
    private final PolicyExclusionJpaRepository exclusions;
    private final PiiSearchTokenService searchTokens;
    private final ConsentService consent;
    private final MeterRegistry meters;

    /** Registry participant code; discovery is broadcast, not aimed at one payer. */
    private static final String REGISTRY_CODE = "nhcx-registry";

    /**
     * Step 1 — send the patient an OTP authorising a policy lookup.
     *
     * @param identifier ABHA address or mobile; forwarded, never persisted
     * @return correlation id threading the discovery flow
     */
    @Transactional
    public String requestDiscoveryOtp(UUID patientId, String identifier) {
        if (!consent.hasConsent(patientId, ConsentPurpose.INSURANCE_CLAIM)) {
            consent.grant(patientId, ConsentPurpose.INSURANCE_CLAIM, "v1.0", "en",
                          ConsentPurpose.INSURANCE_CLAIM.getNoticeSummary(), "VERBAL_IN_PERSON",
                          null, false, false, null);
        }
        consent.requireConsent(patientId, ConsentPurpose.INSURANCE_CLAIM);

        String correlationId = UUID.randomUUID().toString();
        Map<String, Object> bundle = Map.of(
            "resourceType", "CoverageEligibilityRequest",
            "purpose", List.of("discovery"),
            "identifier", identifier);

        nhcx.requestDiscoveryOtp(bundle, REGISTRY_CODE, correlationId);

        counter("otp_requested").increment();
        // The identifier is the thing being protected; only the surrogate id is logged.
        log.info("nhcx.discovery.otp.requested patientId[{}] correlationId[{}]",
                 patientId, correlationId);
        return correlationId;
    }

    /**
     * Step 2 — confirm the OTP, releasing the discovery result.
     *
     * <p>Returns the correlation id. The policies themselves arrive on the NHCX
     * callback and are persisted by {@link #recordDiscoveredPolicies}.
     */
    @Transactional
    public String confirmDiscovery(UUID patientId, String correlationId, String otp) {
        if (otp == null || otp.isBlank()) {
            throw new BusinessRuleViolationException(
                "The patient's OTP is required before policies can be discovered");
        }

        Map<String, Object> bundle = Map.of(
            "resourceType", "CoverageEligibilityRequest",
            "purpose", List.of("discovery"),
            "otp", otp);

        nhcx.confirmDiscoveryOtp(bundle, REGISTRY_CODE, correlationId);

        counter("otp_confirmed").increment();
        log.info("nhcx.discovery.otp.confirmed patientId[{}] correlationId[{}]",
                 patientId, correlationId);
        return correlationId;
    }

    /**
     * Persist policies returned by the registry.
     *
     * <p>Called from the NHCX callback path. Idempotent on correlation id: a
     * gateway retry must not double the list the desk sees.
     */
    @Transactional
    public List<DiscoveredPolicyEntity> recordDiscoveredPolicies(
            UUID patientId, String correlationId, List<DiscoveredPolicy> results) {

        if (!discovered.findByCorrelationId(correlationId).isEmpty()) {
            log.info("nhcx.discovery.duplicate correlationId[{}] ignored", correlationId);
            return discovered.findByCorrelationId(correlationId);
        }

        List<DiscoveredPolicyEntity> saved = results.stream().map(r -> {
            DiscoveredPolicyEntity e = new DiscoveredPolicyEntity();
            e.setPatientId(patientId);
            e.setCorrelationId(correlationId);
            e.setPayerCode(r.payerCode());
            e.setPayerName(r.payerName());
            e.setTpaName(r.tpaName());
            e.setPolicyNumber(r.policyNumber());
            e.setPolicyNumberToken(searchTokens.token(r.policyNumber()));
            e.setMemberId(r.memberId());
            e.setMemberIdToken(searchTokens.token(r.memberId()));
            e.setPolicyType(r.policyType());
            e.setPolicyStartDate(r.startDate());
            e.setPolicyEndDate(r.endDate());
            e.setPrimaryInsuredName(r.primaryInsuredName());
            e.setRelationship(r.relationship());
            return discovered.save(e);
        }).toList();

        counter("policies_found").increment(saved.size());
        log.info("nhcx.discovery.recorded patientId[{}] correlationId[{}] count[{}]",
                 patientId, correlationId, saved.size());
        return saved;
    }

    /** Link a discovered policy to an insurance row a human has accepted. */
    @Transactional
    public DiscoveredPolicyEntity linkToInsurance(UUID discoveredId, UUID insuranceId) {
        DiscoveredPolicyEntity e = discovered.findById(discoveredId)
            .orElseThrow(() -> new ResourceNotFoundException("Discovered policy", discoveredId));

        if (e.getLinkedInsuranceId() != null) {
            throw new BusinessRuleViolationException("This policy is already linked");
        }

        e.setLinkedInsuranceId(insuranceId);
        e.setLinkedAt(Instant.now());
        log.info("nhcx.discovery.linked discoveredId[{}] insuranceId[{}]", discoveredId, insuranceId);
        return discovered.save(e);
    }

    /**
     * Trigger a coverage and benefit check — Screen 2.1.
     *
     * <p>Writes the correlation record before the HTTP call, so a callback that
     * beats the response still finds something to match against.
     */
    @Transactional
    public String checkCoverage(UUID patientId, UUID insuranceId, UUID encounterId,
                                String payerCode, Map<String, Object> bundle) {
        consent.requireConsent(patientId, ConsentPurpose.INSURANCE_CLAIM);

        String correlationId = UUID.randomUUID().toString();

        PolicyCoverageEntity coverage = new PolicyCoverageEntity();
        coverage.setPatientId(patientId);
        coverage.setInsuranceId(insuranceId);
        coverage.setEncounterId(encounterId);
        coverage.setCorrelationId(correlationId);
        coverage.setPayerCode(payerCode);
        coverage.setPolicyStatus("UNKNOWN");
        coverage.setCheckedAt(Instant.now());
        coverages.save(coverage);

        nhcx.submitEligibility(bundle, payerCode, correlationId);

        counter("coverage_checked").increment();
        log.info("nhcx.coverage.requested patientId[{}] correlationId[{}] payer[{}]",
                 patientId, correlationId, payerCode);
        return correlationId;
    }

    /**
     * Apply a payer's eligibility response to the pending snapshot.
     *
     * <p>Each check is preserved rather than overwritten: the hospital may later
     * need to show what it was told at the moment of admission.
     */
    @Transactional
    public PolicyCoverageEntity applyCoverageResponse(String correlationId, JsonNode response) {
        PolicyCoverageEntity coverage = coverages.findByCorrelationId(correlationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No coverage check pending for correlation " + correlationId));

        parser.applyTo(coverage, response);
        PolicyCoverageEntity saved = coverages.save(coverage);

        exclusions.deleteAll(exclusions.findByCoverageId(saved.getId()));
        for (PolicyExclusionEntity ex : parser.exclusionsFrom(response)) {
            ex.setCoverageId(saved.getId());
            exclusions.save(ex);
        }

        counter("coverage_applied").increment();
        log.info("nhcx.coverage.applied correlationId[{}] status[{}]",
                 correlationId, saved.getPolicyStatus());
        return saved;
    }

    public List<DiscoveredPolicyEntity> discoveredFor(UUID patientId) {
        return discovered.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    public List<PolicyCoverageEntity> coverageHistoryFor(UUID patientId) {
        return coverages.findByPatientIdOrderByCheckedAtDesc(patientId);
    }

    public PolicyCoverageEntity latestCoverageFor(UUID patientId) {
        return coverages.findByPatientIdOrderByCheckedAtDesc(patientId).stream()
            .findFirst().orElse(null);
    }

    public List<PolicyExclusionEntity> exclusionsFor(UUID coverageId) {
        return exclusions.findByCoverageId(coverageId);
    }

    private Counter counter(String event) {
        return Counter.builder("hms.nhcx.policy.events").tag("event", event).register(meters);
    }

    /** One policy as the registry described it. */
    public record DiscoveredPolicy(
        String payerCode, String payerName, String tpaName,
        String policyNumber, String memberId, String policyType,
        java.time.LocalDate startDate, java.time.LocalDate endDate,
        String primaryInsuredName, String relationship) {
    }
}
