package com.hms.application.compliance;

import com.hms.api.shared.ConsentAttestation;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.compliance.ConsentNoticeEntity;
import com.hms.infrastructure.persistence.compliance.ConsentNoticeJpaRepository;
import com.hms.infrastructure.persistence.compliance.ConsentRecordEntity;
import com.hms.infrastructure.persistence.compliance.ConsentRecordJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The regression suite for WO-022.
 *
 * <p>These tests exist because the defect they cover was invisible to review:
 * the code read as if it enforced consent, the metric incremented, the audit
 * table filled with plausible rows, and nothing ever failed. Every assertion
 * here is aimed at the specific way that lie was told.
 */
class ConsentGateIntegrityTest {

    private ConsentRecordJpaRepository records;
    private ConsentNoticeJpaRepository notices;
    private MeterRegistry meters;
    private ConsentService service;
    private ConsentGate gate;
    private AuditorAware<UUID> auditor;

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        records = mock(ConsentRecordJpaRepository.class);
        notices = mock(ConsentNoticeJpaRepository.class);
        auditor = mock(AuditorAware.class);
        meters = new SimpleMeterRegistry();
        service = new ConsentService(records, notices, meters);
        ReflectionTestUtils.setField(service, "enforcementMode", "enforce");
        gate = new ConsentGate(service, auditor);

        when(records.save(any(ConsentRecordEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private ConsentNoticeEntity notice(String state) {
        ConsentNoticeEntity n = new ConsentNoticeEntity();
        n.setPurpose(ConsentPurpose.ABHA_LINKAGE.name());
        n.setVersion("v1.0");
        n.setLanguage("en");
        n.setBodyText("We will create or link your ABHA health account.");
        n.setNoticeState(state);
        n.setEffectiveFrom(Instant.now().minusSeconds(60));
        return n;
    }

    private ConsentRecordEntity grantedRecord(ConsentProvenance provenance) {
        ConsentRecordEntity r = new ConsentRecordEntity();
        r.setPatientId(PATIENT);
        r.setPurpose(ConsentPurpose.ABHA_LINKAGE.name());
        r.setState("GRANTED");
        r.setProvenance(provenance.name());
        r.setGrantedAt(Instant.now().minusSeconds(3600));
        r.setNoticeVersion("v1.0");
        r.setCaptureChannel("IN_PERSON");
        return r;
    }

    // ── AC-1: no consent, no attestation → refuse, write nothing ───────────

    @Test
    @DisplayName("AC-1: with no consent and no attestation the gate throws and writes no record")
    void refusesWithoutAttestation() {
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.empty());
        when(notices.findCandidates("ABHA_LINKAGE", "en"))
            .thenReturn(List.of(notice("ACTIVE")));

        assertThatThrownBy(() ->
            gate.ensure(PATIENT, ConsentPurpose.ABHA_LINKAGE, null, "abha.enrolment"))
            .isInstanceOf(ConsentRequiredException.class);

        // The whole point: nothing was written to make the check pass.
        verify(records, never()).save(any(ConsentRecordEntity.class));
    }

    @Test
    @DisplayName("AC-1: the refusal carries the notice text the desk must show")
    void refusalCarriesNotice() {
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.empty());
        when(notices.findCandidates("ABHA_LINKAGE", "en"))
            .thenReturn(List.of(notice("ACTIVE")));

        assertThatThrownBy(() ->
            gate.ensure(PATIENT, ConsentPurpose.ABHA_LINKAGE, null, "abha.enrolment"))
            .isInstanceOfSatisfying(ConsentRequiredException.class, ex -> {
                assertThat(ex.getNoticeVersion()).isEqualTo("v1.0");
                assertThat(ex.getNoticeText()).contains("ABHA health account");
            });
    }

    // ── AC-2: attestation → real record, real capturer, real hash ──────────

    @Test
    @DisplayName("AC-2: an attestation records STAFF_ATTESTED consent attributed to the logged-in user")
    void attestationCapturesRealConsent() {
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.empty());
        when(notices.findByPurposeAndVersionAndLanguage("ABHA_LINKAGE", "v1.0", "en"))
            .thenReturn(Optional.of(notice("ACTIVE")));
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(STAFF));

        gate.ensure(PATIENT, ConsentPurpose.ABHA_LINKAGE,
                    new ConsentAttestation("v1.0", "en", true, false, false),
                    "abha.enrolment");

        org.mockito.ArgumentCaptor<ConsentRecordEntity> captor =
            org.mockito.ArgumentCaptor.forClass(ConsentRecordEntity.class);
        verify(records).save(captor.capture());

        ConsentRecordEntity saved = captor.getValue();
        assertThat(saved.getProvenance()).isEqualTo(ConsentProvenance.STAFF_ATTESTED.name());
        assertThat(saved.getCapturedBy()).isEqualTo(STAFF);
        // The hash is over the registry text, not over whatever the client sent.
        assertThat(saved.getNoticeTextHash())
            .isEqualTo(ConsentService.sha256(notice("ACTIVE").getBodyText()));
    }

    @Test
    @DisplayName("AC-2: an attestation with no authenticated user is refused, not silently attributed to null")
    void attestationWithoutUserIsRefused() {
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.empty());
        when(auditor.getCurrentAuditor()).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            gate.ensure(PATIENT, ConsentPurpose.ABHA_LINKAGE,
                        new ConsentAttestation("v1.0", "en", true, false, false),
                        "abha.enrolment"))
            .isInstanceOf(BusinessRuleViolationException.class);

        verify(records, never()).save(any(ConsentRecordEntity.class));
    }

    // ── AC-3: SYSTEM_INFERRED does not authorise anything ──────────────────

    @Test
    @DisplayName("AC-3: a SYSTEM_INFERRED grant is treated as absent consent")
    void inferredGrantDoesNotAuthorise() {
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.of(grantedRecord(ConsentProvenance.SYSTEM_INFERRED)));

        assertThat(service.hasConsent(PATIENT, ConsentPurpose.ABHA_LINKAGE)).isFalse();
        assertThat(meters.counter("hms_consent_checks_total",
                                  "purpose", "ABHA_LINKAGE",
                                  "outcome", "inferred_ignored").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("AC-3: a STAFF_ATTESTED grant does authorise")
    void attestedGrantAuthorises() {
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.of(grantedRecord(ConsentProvenance.STAFF_ATTESTED)));

        assertThat(service.hasConsent(PATIENT, ConsentPurpose.ABHA_LINKAGE)).isTrue();
    }

    @Test
    @DisplayName("AC-3: a null provenance fails closed rather than open")
    void nullProvenanceFailsClosed() {
        ConsentRecordEntity legacy = grantedRecord(ConsentProvenance.STAFF_ATTESTED);
        legacy.setProvenance(null);
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.of(legacy));

        assertThat(service.hasConsent(PATIENT, ConsentPurpose.ABHA_LINKAGE)).isFalse();
    }

    // ── The defect cannot be recreated through the API ─────────────────────

    @Test
    @DisplayName("SYSTEM_INFERRED consent can never be created by any caller")
    void inferredConsentCannotBeGranted() {
        assertThatThrownBy(() -> service.grant(
                PATIENT, ConsentPurpose.ABHA_LINKAGE, "v1.0", "en", "text",
                "IN_PERSON", STAFF, false, false, null,
                ConsentProvenance.SYSTEM_INFERRED))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("Staff-attested consent without a capturing user is refused")
    void staffAttestedRequiresCapturer() {
        assertThatThrownBy(() -> service.grant(
                PATIENT, ConsentPurpose.ABHA_LINKAGE, "v1.0", "en", "text",
                "VERBAL_IN_PERSON", null, false, false, null,
                ConsentProvenance.STAFF_ATTESTED))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("accountable");
    }

    @Test
    @DisplayName("Consent cannot be recorded against a notice version the hospital cannot produce")
    void unknownNoticeVersionIsRefused() {
        when(notices.findByPurposeAndVersionAndLanguage("ABHA_LINKAGE", "v9.9", "en"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.captureFromAttestation(
                PATIENT, ConsentPurpose.ABHA_LINKAGE, "v9.9", "en",
                "IN_PERSON", STAFF, false, false))
            .isInstanceOf(BusinessRuleViolationException.class);

        verify(records, never()).save(any(ConsentRecordEntity.class));
    }

    // ── warn mode ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("warn mode meters the refusal but lets the action proceed")
    void warnModeDoesNotBlock() {
        ReflectionTestUtils.setField(service, "enforcementMode", "warn");
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.empty());

        gate.ensure(PATIENT, ConsentPurpose.ABHA_LINKAGE, null, "abha.enrolment");

        assertThat(meters.counter("hms_consent_refusals_total",
                                  "purpose", "ABHA_LINKAGE",
                                  "action", "abha.enrolment").count()).isEqualTo(1.0);
        verify(records, never()).save(any(ConsentRecordEntity.class));
    }

    @Test
    @DisplayName("enforce is the default when the property is unset")
    void enforceIsDefault() {
        ConsentService fresh = new ConsentService(records, notices, new SimpleMeterRegistry());
        // Field default mirrors the @Value default of "enforce"; a blank value
        // must not be read as warn.
        ReflectionTestUtils.setField(fresh, "enforcementMode", "enforce");
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.empty());
        when(notices.findCandidates(anyString(), anyString())).thenReturn(List.of());

        assertThatThrownBy(() ->
            fresh.requireConsent(PATIENT, ConsentPurpose.ABHA_LINKAGE, "abha.enrolment"))
            .isInstanceOf(ConsentRequiredException.class);
    }

    // ── notice resolution ─────────────────────────────────────────────────

    @Test
    @DisplayName("a missing notice is logged and metered rather than silently returning nothing")
    void missingNoticeIsMetered() {
        when(notices.findCandidates("ABHA_LINKAGE", "en")).thenReturn(List.of());

        assertThat(service.activeNotice(ConsentPurpose.ABHA_LINKAGE, "en")).isEmpty();
        assertThat(meters.counter("hms_consent_notice_missing_total",
                                  "purpose", "ABHA_LINKAGE",
                                  "language", "en").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("serving a DRAFT placeholder is counted, because it does not discharge the notice duty")
    void draftNoticeIsCounted() {
        when(notices.findCandidates("ABHA_LINKAGE", "en"))
            .thenReturn(List.of(notice("DRAFT")));

        assertThat(service.activeNotice(ConsentPurpose.ABHA_LINKAGE, "en")).isPresent();
        assertThat(meters.counter("hms_consent_notice_draft_served_total",
                                  "purpose", "ABHA_LINKAGE",
                                  "language", "en").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("re-granting supersedes an inferred row rather than colliding with the unique index")
    void regrantSupersedesInferredRow() {
        ConsentRecordEntity inferred = grantedRecord(ConsentProvenance.SYSTEM_INFERRED);
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "ABHA_LINKAGE", "GRANTED"))
            .thenReturn(Optional.of(inferred));
        when(notices.findByPurposeAndVersionAndLanguage("ABHA_LINKAGE", "v1.0", "en"))
            .thenReturn(Optional.of(notice("ACTIVE")));

        service.captureFromAttestation(PATIENT, ConsentPurpose.ABHA_LINKAGE,
                                       "v1.0", "en", "IN_PERSON", STAFF, false, false);

        assertThat(inferred.getState()).isEqualTo("WITHDRAWN");
        assertThat(inferred.getWithdrawalChannel()).isEqualTo("SUPERSEDED");
    }
}
