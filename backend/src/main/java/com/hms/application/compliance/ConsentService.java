package com.hms.application.compliance;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.compliance.ConsentRecordEntity;
import com.hms.infrastructure.persistence.compliance.ConsentRecordJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Capture, check and withdraw patient consent.
 *
 * <p>{@link #hasConsent} is the gate every automated interaction must pass
 * through. It is deliberately a hard boolean with no "assume yes if unknown"
 * branch: absent consent is not consent, and a system that defaults to
 * permitted will process a patient who never agreed and produce no evidence
 * either way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRecordJpaRepository repository;
    private final MeterRegistry meterRegistry;

    /**
     * Whether this patient currently permits this purpose.
     *
     * <p>The counter is here rather than at call sites so that "how often did we
     * decline to act for lack of consent" is answerable — a number that should be
     * watched, because a sudden spike usually means a consent capture step broke
     * rather than that patients changed their minds.
     */
    @Transactional(readOnly = true)
    public boolean hasConsent(UUID patientId, ConsentPurpose purpose) {
        boolean granted = repository
            .findByPatientIdAndPurposeAndState(patientId, purpose.name(), "GRANTED")
            .map(c -> c.isActive(Instant.now()))
            .orElse(false);

        meterRegistry.counter("hms_consent_checks_total",
                              "purpose", purpose.name(),
                              "outcome", granted ? "granted" : "absent").increment();
        return granted;
    }

    /** Throws rather than returns, for call sites where proceeding is not an option. */
    @Transactional(readOnly = true)
    public void requireConsent(UUID patientId, ConsentPurpose purpose) {
        if (!hasConsent(patientId, purpose)) {
            throw new ConsentRequiredException(purpose);
        }
    }

    @Transactional
    public ConsentRecordEntity grant(UUID patientId, ConsentPurpose purpose,
                                     String noticeVersion, String noticeLanguage,
                                     String noticeText, String captureChannel,
                                     UUID capturedBy, boolean minor,
                                     boolean guardianVerified, Instant expiresAt) {

        if (minor && !guardianVerified) {
            // DPDP requires verifiable parental consent for a child's data.
            throw new BusinessRuleViolationException(
                "A minor's consent requires verified guardian approval");
        }
        if (noticeVersion == null || noticeVersion.isBlank()) {
            // Without the version there is no way to show later what the patient
            // actually agreed to, which makes the record close to worthless.
            throw new BusinessRuleViolationException(
                "Notice version is required — it is what makes the consent informed");
        }

        // Re-granting supersedes rather than duplicates: the unique index allows
        // only one live grant per purpose.
        repository.findByPatientIdAndPurposeAndState(patientId, purpose.name(), "GRANTED")
            .ifPresent(existing -> {
                existing.setState("WITHDRAWN");
                existing.setWithdrawnAt(Instant.now());
                existing.setWithdrawalChannel("SUPERSEDED");
                repository.save(existing);
            });

        ConsentRecordEntity record = new ConsentRecordEntity();
        record.setPatientId(patientId);
        record.setPurpose(purpose.name());
        record.setState("GRANTED");
        record.setNoticeVersion(noticeVersion);
        record.setNoticeLanguage(noticeLanguage == null ? "en" : noticeLanguage);
        record.setNoticeTextHash(noticeText == null ? null : sha256(noticeText));
        record.setCaptureChannel(captureChannel);
        record.setCapturedBy(capturedBy);
        record.setGrantedAt(Instant.now());
        record.setExpiresAt(expiresAt);
        record.setMinor(minor);
        record.setGuardianVerified(guardianVerified);

        ConsentRecordEntity saved = repository.save(record);
        meterRegistry.counter("hms_consent_grants_total", "purpose", purpose.name()).increment();
        // Patient id only. Never the name, never the notice text.
        log.info("consent.granted patient[{}] purpose[{}] channel[{}] noticeVersion[{}]",
                 patientId, purpose, captureChannel, noticeVersion);
        return saved;
    }

    /**
     * Withdraw consent.
     *
     * <p>Must be at least as easy as granting it — a withdrawal path harder than
     * the grant path is not a real withdrawal path. Idempotent, because a patient
     * pressing "stop" twice should not error.
     */
    @Transactional
    public void withdraw(UUID patientId, ConsentPurpose purpose, String channel, UUID actor) {
        repository.findByPatientIdAndPurposeAndState(patientId, purpose.name(), "GRANTED")
            .ifPresent(record -> {
                record.setState("WITHDRAWN");
                record.setWithdrawnAt(Instant.now());
                record.setWithdrawnBy(actor);
                record.setWithdrawalChannel(channel);
                repository.save(record);
                meterRegistry.counter("hms_consent_withdrawals_total",
                                      "purpose", purpose.name()).increment();
                log.info("consent.withdrawn patient[{}] purpose[{}] channel[{}]",
                         patientId, purpose, channel);
            });
    }

    @Transactional(readOnly = true)
    public List<ConsentRecordEntity> historyFor(UUID patientId) {
        return repository.findByPatientIdOrderByGrantedAtDesc(patientId);
    }

    /**
     * Mark lapsed grants EXPIRED.
     *
     * <p>Runs on a scheduled thread with no tenant context, so the query is
     * deliberately tenant-agnostic — expiry must apply to every tenant. The state
     * change matters even though {@code isActive} already checks the timestamp:
     * without it the unique "one live grant" index would keep blocking a fresh
     * grant after the old one lapsed.
     */
    @Scheduled(cron = "0 15 2 * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireLapsedConsents() {
        try {
            List<ConsentRecordEntity> expired = repository.findExpired(Instant.now());
            for (ConsentRecordEntity record : expired) {
                record.setState("EXPIRED");
                repository.save(record);
            }
            if (!expired.isEmpty()) {
                log.info("consent.expired count[{}]", expired.size());
            }
        } catch (RuntimeException e) {
            log.error("consent.expiry.failed", e);
        }
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
