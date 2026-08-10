package com.hms.application.abha;

import com.hms.application.compliance.ConsentPurpose;
import com.hms.application.compliance.ConsentService;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.abdm.AbdmClient;
import com.hms.infrastructure.persistence.abha.AbhaLinkageEntity;
import com.hms.infrastructure.persistence.abha.AbhaLinkageJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ABHA identity lifecycle for a patient.
 *
 * <p>Screens 1.1 of the ABDM requirement set sit on top of this: search an
 * existing ABHA address, send an OTP, verify it, and attach the resulting
 * identity to the patient master.
 *
 * <h2>Two consents, not one</h2>
 * ABDM captures its own enrolment consent inside the gateway flow. That is
 * <em>not</em> the same as the hospital's DPDP obligation to record why it is
 * processing a national health id. Both are required, they have different
 * lifecycles, and this service will not start an enrolment until the DPDP
 * consent exists — see {@link ConsentPurpose#ABHA_LINKAGE}.
 *
 * <h2>What is never persisted</h2>
 * Aadhaar. It goes to {@link AbdmClient} for the OTP and is discarded. It is not
 * a field, not a log value, and not an exception message anywhere on this path.
 * The ABHA number and address that come back <em>are</em> stored, encrypted,
 * with blind-index tokens beside them because ciphertext is not searchable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbhaService {

    /** Enrolment started, OTP sent, awaiting verification. */
    public static final String STATE_PENDING_OTP = "PENDING_OTP";
    /** ABHA number held and attached to the patient. */
    public static final String STATE_LINKED = "LINKED";
    /** Gateway rejected the attempt. Terminal until a fresh attempt starts. */
    public static final String STATE_FAILED = "FAILED";

    private final AbdmClient abdm;
    private final AbhaLinkageJpaRepository repository;
    private final PiiSearchTokenService searchTokens;
    private final ConsentService consent;
    private final MeterRegistry meters;

    /** Which identifier the front desk offered for the OTP challenge. */
    public enum OtpChannel { AADHAAR, MOBILE }

    /**
     * Begin enrolment by sending an OTP.
     *
     * <p>Refuses if the patient already holds a linked ABHA — re-enrolling would
     * mint a second national id for one person, which ABDM treats as a data
     * quality incident and which no screen in the requirement set asks for.
     *
     * @param loginId Aadhaar or mobile depending on {@code channel}; never stored
     */
    @Transactional
    public AbhaLinkageEntity startEnrolment(UUID patientId, OtpChannel channel, String loginId) {
        consent.requireConsent(patientId, ConsentPurpose.ABHA_LINKAGE);

        repository.findByPatientIdAndLinkageState(patientId, STATE_LINKED)
            .ifPresent(existing -> {
                throw new BusinessRuleViolationException(
                    "This patient already has a linked ABHA identity");
            });

        AbdmClient.OtpChallenge challenge = channel == OtpChannel.AADHAAR
            ? abdm.requestAadhaarOtp(loginId)
            : abdm.requestMobileOtp(loginId);

        AbhaLinkageEntity linkage = new AbhaLinkageEntity();
        linkage.setPatientId(patientId);
        linkage.setLinkageState(STATE_PENDING_OTP);
        linkage.setTransactionId(challenge.transactionId());
        linkage.setConsentRecordedAt(Instant.now());
        linkage.setConsentVersion(ConsentPurpose.ABHA_LINKAGE.name());

        AbhaLinkageEntity saved = repository.save(linkage);

        counter("started", channel.name()).increment();
        // patientId is a surrogate key, safe to log. loginId is not, and is absent.
        log.info("abha.enrolment.started patientId[{}] linkageId[{}] channel[{}]",
                 patientId, saved.getId(), channel);
        return saved;
    }

    /**
     * Verify the OTP and attach the resulting ABHA identity.
     *
     * <p>ABDM reporting that an identity already exists is a normal outcome, not
     * an error — most adults already hold an ABHA — so it lands here as a
     * populated identity and is linked exactly like a fresh enrolment.
     */
    @Transactional
    public AbhaLinkageEntity verifyOtp(UUID linkageId, String otp, String mobile) {
        AbhaLinkageEntity linkage = repository.findById(linkageId)
            .orElseThrow(() -> new ResourceNotFoundException("ABHA linkage", linkageId));

        if (!STATE_PENDING_OTP.equals(linkage.getLinkageState())) {
            throw new BusinessRuleViolationException(
                "This ABHA enrolment is not awaiting an OTP");
        }

        try {
            AbdmClient.AbhaIdentity identity =
                abdm.verifyOtpAndEnrol(linkage.getTransactionId(), otp, mobile);

            if (identity.abhaNumber() == null || identity.abhaNumber().isBlank()) {
                throw new BusinessRuleViolationException(
                    "ABDM completed the enrolment without returning an ABHA number");
            }

            linkage.setAbhaNumber(identity.abhaNumber());
            linkage.setAbhaNumberToken(searchTokens.token(identity.abhaNumber()));
            linkage.setAbhaAddress(identity.abhaAddress());
            linkage.setAbhaAddressToken(identity.abhaAddress() == null
                                        ? null
                                        : searchTokens.token(identity.abhaAddress()));
            linkage.setLinkageState(STATE_LINKED);
            linkage.setLinkedAt(Instant.now());
            linkage.setFailureCode(null);

            AbhaLinkageEntity saved = repository.save(linkage);
            counter("linked", "OK").increment();
            log.info("abha.enrolment.linked patientId[{}] linkageId[{}]",
                     saved.getPatientId(), saved.getId());
            return saved;

        } catch (RuntimeException e) {
            // Record the failure so the desk can see why, then rethrow. The
            // exception message is not stored: ABDM error bodies can echo the
            // submitted Aadhaar.
            linkage.setLinkageState(STATE_FAILED);
            linkage.setFailureCode(e.getClass().getSimpleName());
            repository.save(linkage);

            counter("failed", e.getClass().getSimpleName()).increment();
            log.warn("abha.enrolment.failed patientId[{}] linkageId[{}] type[{}]",
                     linkage.getPatientId(), linkage.getId(), e.getClass().getSimpleName());
            throw e;
        }
    }

    /** Whether an ABHA address is already taken, for the "suggest an address" field. */
    public boolean abhaAddressAvailable(String abhaAddress) {
        return !abdm.abhaAddressExists(abhaAddress);
    }

    /** Every linkage attempt for a patient, newest first. Drives the verified badge. */
    public List<AbhaLinkageEntity> historyFor(UUID patientId) {
        return repository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }

    /** The patient's active ABHA identity, if any. */
    public AbhaLinkageEntity linkedFor(UUID patientId) {
        return repository.findByPatientIdAndLinkageState(patientId, STATE_LINKED)
            .orElse(null);
    }

    private Counter counter(String outcome, String detail) {
        return Counter.builder("hms.abha.link.attempts")
            .tag("outcome", outcome)
            .tag("detail", detail)
            .register(meters);
    }
}
