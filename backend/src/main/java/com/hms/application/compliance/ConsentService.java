package com.hms.application.compliance;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.compliance.ConsentNoticeEntity;
import com.hms.infrastructure.persistence.compliance.ConsentNoticeJpaRepository;
import com.hms.infrastructure.persistence.compliance.ConsentRecordEntity;
import com.hms.infrastructure.persistence.compliance.ConsentRecordJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Capture, check and withdraw patient consent.
 *
 * <p>{@link #hasConsent} is the gate every automated interaction must pass
 * through. It is deliberately a hard boolean with no "assume yes if unknown"
 * branch: absent consent is not consent, and a system that defaults to
 * permitted will process a patient who never agreed and produce no evidence
 * either way.
 *
 * <h2>What changed in V205 / WO-022</h2>
 *
 * <p>The docstring above was already true of this class, and was being defeated
 * by its callers. Four call sites granted the consent they were about to check,
 * making {@link #requireConsent} incapable of throwing. Two changes close that:
 *
 * <ul>
 *   <li>{@link #hasConsent} ignores {@code SYSTEM_INFERRED} grants, so the rows
 *       that defect produced no longer authorise anything.</li>
 *   <li>{@link #grant} demands a {@code capturedBy} for staff-attested consent.
 *       A grant nobody is accountable for is refused outright, which is what
 *       makes the old pattern impossible to reintroduce by accident.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRecordJpaRepository repository;
    private final ConsentNoticeJpaRepository noticeRepository;
    private final MeterRegistry meterRegistry;

    /**
     * {@code enforce} (default) or {@code warn}.
     *
     * <p>In {@code warn} the gate meters and logs a refusal but lets the action
     * proceed. It exists so a hospital can measure how often consent is actually
     * missing before the gate starts blocking a live front desk, and it is
     * deliberately not the default: a flag that disables the gate is a flag
     * somebody leaves off, and a gate that does not gate is the defect this work
     * order exists to fix. Anything other than {@code warn} means enforce.
     */
    @Value("${hms.consent.enforcement:enforce}")
    private String enforcementMode;

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
        Optional<ConsentRecordEntity> record = repository
            .findByPatientIdAndPurposeAndState(patientId, purpose.name(), "GRANTED");

        boolean live = record.map(c -> c.isActive(Instant.now())).orElse(false);
        boolean reliable = record.map(c -> provenanceOf(c).isReliable()).orElse(false);
        boolean granted = live && reliable;

        String outcome;
        if (granted) {
            outcome = "granted";
        } else if (live) {
            // A grant exists and has not lapsed, but it was manufactured by the
            // pre-V205 defect. Distinguished from plain absence so the burndown
            // is visible rather than hiding inside "absent".
            outcome = "inferred_ignored";
            log.warn("event=consent.inferred.blocked patient_id={} purpose={}", patientId, purpose);
        } else {
            outcome = "absent";
        }

        meterRegistry.counter("hms_consent_checks_total",
                              "purpose", purpose.name(),
                              "outcome", outcome).increment();
        return granted;
    }

    /**
     * Throws rather than returns, for call sites where proceeding is not an
     * option.
     *
     * <p>Resolves the notice so the caller's 409 can carry the text the desk must
     * show. In {@code warn} mode it records the refusal and returns instead of
     * throwing.
     */
    @Transactional(readOnly = true)
    public void requireConsent(UUID patientId, ConsentPurpose purpose, String action) {
        if (hasConsent(patientId, purpose)) {
            return;
        }

        meterRegistry.counter("hms_consent_refusals_total",
                              "purpose", purpose.name(),
                              "action", action == null ? "unspecified" : action).increment();
        log.warn("event=consent.refused patient_id={} purpose={} action={} mode={}",
                 patientId, purpose, action, enforcementMode);

        if (isWarnOnly()) {
            return;
        }

        Optional<ConsentNoticeEntity> notice = activeNotice(purpose, "en");
        throw notice
            .map(n -> new ConsentRequiredException(purpose, n.getVersion(),
                                                   n.getLanguage(), n.getBodyText()))
            .orElseGet(() -> new ConsentRequiredException(purpose));
    }

    /** Overload for call sites that have no meaningful action label. */
    @Transactional(readOnly = true)
    public void requireConsent(UUID patientId, ConsentPurpose purpose) {
        requireConsent(patientId, purpose, null);
    }

    private boolean isWarnOnly() {
        return "warn".equalsIgnoreCase(enforcementMode);
    }

    /**
     * The notice currently in force for a purpose and language.
     *
     * <p>Prefers ACTIVE over DRAFT. A DRAFT is a V205 placeholder carried over
     * from the enum summaries; serving it keeps the desk working but does not
     * discharge the notice obligation, which is why
     * {@code hms_consent_notice_draft_served_total} exists.
     */
    @Transactional(readOnly = true)
    public Optional<ConsentNoticeEntity> activeNotice(ConsentPurpose purpose, String language) {
        String lang = (language == null || language.isBlank()) ? "en" : language;
        List<ConsentNoticeEntity> candidates = noticeRepository.findCandidates(purpose.name(), lang);
        Instant now = Instant.now();

        Optional<ConsentNoticeEntity> found = candidates.stream()
            .filter(n -> n.isLive(now))
            .findFirst();

        if (found.isEmpty()) {
            // The desk is about to be hard-blocked and no amount of retrying
            // will help, so this is ERROR rather than WARN.
            log.error("event=consent.notice.missing purpose={} language={}", purpose, lang);
            meterRegistry.counter("hms_consent_notice_missing_total",
                                  "purpose", purpose.name(), "language", lang).increment();
        } else if ("DRAFT".equals(found.get().getNoticeState())) {
            meterRegistry.counter("hms_consent_notice_draft_served_total",
                                  "purpose", purpose.name(), "language", lang).increment();
        }
        return found;
    }

    /**
     * Record consent.
     *
     * <p>{@code capturedBy} is mandatory for {@code STAFF_ATTESTED}. That single
     * check is what stops the old self-granting pattern coming back: a service
     * cannot manufacture a grant without naming a user who is accountable for it,
     * and no service has one to hand.
     */
    @Transactional
    public ConsentRecordEntity grant(UUID patientId, ConsentPurpose purpose,
                                     String noticeVersion, String noticeLanguage,
                                     String noticeText, String captureChannel,
                                     UUID capturedBy, boolean minor,
                                     boolean guardianVerified, Instant expiresAt,
                                     ConsentProvenance provenance) {

        ConsentProvenance prov = provenance == null ? ConsentProvenance.STAFF_ATTESTED : provenance;

        if (prov == ConsentProvenance.SYSTEM_INFERRED) {
            // Nothing may write one of these again. The value exists only to
            // describe rows the V205 backfill labelled.
            throw new BusinessRuleViolationException(
                "SYSTEM_INFERRED consent cannot be created — consent must be attested by a person");
        }
        if (prov == ConsentProvenance.STAFF_ATTESTED && capturedBy == null) {
            throw new BusinessRuleViolationException(
                "Staff-attested consent requires the capturing user — "
                + "a consent record nobody is accountable for is not evidence of consent");
        }
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
        // only one live grant per purpose. This also covers superseding a
        // SYSTEM_INFERRED row when the patient is finally asked properly.
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
        record.setProvenance(prov.name());
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
        meterRegistry.counter("hms_consent_grants_total",
                              "purpose", purpose.name(),
                              "provenance", prov.name()).increment();
        // Patient id only. Never the name, never the notice text.
        log.info("event=consent.granted patient_id={} purpose={} channel={} notice_version={} provenance={}",
                 patientId, purpose, captureChannel, noticeVersion, prov);
        return saved;
    }

    /**
     * Capture consent from a staff attestation, resolving the notice text from
     * the registry rather than trusting whatever the client sent.
     *
     * <p>The client tells us <em>which</em> notice version it displayed; the
     * hash is computed over the text this server holds for that version. If the
     * client displayed something else, the versions will not line up and the
     * mismatch is visible later — which is the point of storing a hash at all.
     */
    @Transactional
    public ConsentRecordEntity captureFromAttestation(
            UUID patientId, ConsentPurpose purpose, String noticeVersion,
            String noticeLanguage, String captureChannel, UUID capturedBy,
            boolean minor, boolean guardianVerified) {

        String lang = (noticeLanguage == null || noticeLanguage.isBlank()) ? "en" : noticeLanguage;

        ConsentNoticeEntity notice = noticeRepository
            .findByPurposeAndVersionAndLanguage(purpose.name(), noticeVersion, lang)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "No notice text on file for " + purpose + " version " + noticeVersion
                + " in " + lang + " — consent cannot be recorded against a notice "
                + "this hospital cannot produce"));

        return grant(patientId, purpose, notice.getVersion(), notice.getLanguage(),
                     notice.getBodyText(), captureChannel, capturedBy,
                     minor, guardianVerified, null, ConsentProvenance.STAFF_ATTESTED);
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
                log.info("event=consent.withdrawn patient_id={} purpose={} channel={}",
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
                log.info("event=consent.expired count={}", expired.size());
            }
        } catch (RuntimeException e) {
            log.error("event=consent.expiry.failed", e);
        }
    }

    /**
     * Publishes whether the gate is currently switched off.
     *
     * <p>warn mode is a measurement window, not a resting state, and a flag that
     * disables a compliance control is exactly the thing that gets left on. The
     * gauge lets an alert notice after a week.
     */
    @jakarta.annotation.PostConstruct
    void publishEnforcementMode() {
        meterRegistry.gauge("hms_consent_enforcement_warn_mode", isWarnOnly() ? 1 : 0);
        if (isWarnOnly()) {
            log.warn("event=consent.enforcement.warn_mode "
                     + "msg=\"consent gate is metering refusals but NOT blocking\"");
        }
    }

    /**
     * Publishes how many manufactured grants are still outstanding.
     *
     * <p>Should trend to zero as patients are re-consented. If it stays flat,
     * re-consent is not happening in practice and the alert in WO-022 §6 fires.
     */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void publishInferredRemaining() {
        try {
            long remaining = repository.countLiveByProvenance(
                ConsentProvenance.SYSTEM_INFERRED.name());
            meterRegistry.gauge("hms_consent_inferred_remaining", remaining);
            if (remaining > 0) {
                log.warn("event=consent.inferred.remaining count={}", remaining);
            }
        } catch (RuntimeException e) {
            log.error("event=consent.inferred.gauge.failed", e);
        }
    }

    private static ConsentProvenance provenanceOf(ConsentRecordEntity record) {
        String raw = record.getProvenance();
        if (raw == null || raw.isBlank()) {
            // A null provenance can only be a row written before V205, which is
            // exactly the population that must not be relied on. Failing closed.
            return ConsentProvenance.SYSTEM_INFERRED;
        }
        try {
            return ConsentProvenance.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ConsentProvenance.SYSTEM_INFERRED;
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
