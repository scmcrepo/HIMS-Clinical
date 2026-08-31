package com.hms.application.compliance;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.compliance.ErasureRequestEntity;
import com.hms.infrastructure.persistence.compliance.ErasureRequestJpaRepository;
import com.hms.infrastructure.persistence.compliance.ErasureTargetEntity;
import com.hms.infrastructure.persistence.compliance.ErasureTargetJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * The gate around erasure.
 *
 * <p>Most of these assert that something does <em>not</em> happen. Erasure is
 * irreversible for the DELETE targets, so the interesting failures are the ones
 * where it runs when it should not have.
 */
class DataPrincipalRightsServiceTest {

    private ErasureRequestJpaRepository requests;
    private ErasureTargetJpaRepository targets;
    private ErasureService erasureService;
    private AuditorAware<UUID> auditor;
    private MeterRegistry meters;
    private DataPrincipalRightsService service;

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        requests = mock(ErasureRequestJpaRepository.class);
        targets = mock(ErasureTargetJpaRepository.class);
        erasureService = mock(ErasureService.class);
        auditor = mock(AuditorAware.class);
        meters = new SimpleMeterRegistry();
        service = new DataPrincipalRightsService(
            requests, targets, erasureService, auditor, meters);

        when(requests.save(any(ErasureRequestEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(auditor.getCurrentAuditor()).thenReturn(Optional.of(STAFF));
    }

    private ErasureRequestEntity request(String state, boolean verified) {
        ErasureRequestEntity r = new ErasureRequestEntity();
        r.setPatientId(PATIENT);
        r.setRequestType("ERASURE");
        r.setState(state);
        r.setRequestedAt(Instant.now());
        r.setDueAt(Instant.now().plusSeconds(86400));
        if (verified) {
            r.setRequesterVerifiedAt(Instant.now());
            r.setVerificationMethod("IN_PERSON_ID");
        }
        return r;
    }

    // ── The sweep must not run unverified ─────────────────────────────────

    @Test
    @DisplayName("execute refuses an unverified request and sweeps nothing")
    void refusesUnverifiedExecution() {
        ErasureRequestEntity r = request("RECEIVED", false);
        when(requests.findById(any())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.execute(UUID.randomUUID()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Verify the requester");

        verify(erasureService, never()).sweep(any());
    }

    @Test
    @DisplayName("execute runs the sweep once the requester is verified")
    void runsWhenVerified() {
        ErasureRequestEntity r = request("IN_PROGRESS", true);
        when(requests.findById(any())).thenReturn(Optional.of(r));
        when(erasureService.sweep(r)).thenReturn(List.of());

        service.execute(UUID.randomUUID());

        verify(erasureService).sweep(r);
    }

    @Test
    @DisplayName("execute is idempotent — a completed request is not swept twice")
    void completedRequestIsNotSweptAgain() {
        ErasureRequestEntity r = request("COMPLETED", true);
        UUID id = UUID.randomUUID();
        when(requests.findById(id)).thenReturn(Optional.of(r));
        when(targets.findByRequestIdOrderByTargetStore(id)).thenReturn(List.of());

        service.execute(id);

        verify(erasureService, never()).sweep(any());
    }

    @Test
    @DisplayName("a CORRECTION request never triggers a sweep")
    void correctionDoesNotSweep() {
        ErasureRequestEntity r = request("IN_PROGRESS", true);
        r.setRequestType("CORRECTION");
        when(requests.findById(any())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.execute(UUID.randomUUID()))
            .isInstanceOf(BusinessRuleViolationException.class);

        verify(erasureService, never()).sweep(any());
    }

    // ── Verification ──────────────────────────────────────────────────────

    @Test
    @DisplayName("verification is attributed to the authenticated user")
    void verificationRecordsWho() {
        ErasureRequestEntity r = request("RECEIVED", false);
        when(requests.findById(any())).thenReturn(Optional.of(r));

        ErasureRequestEntity verified = service.verifyRequester(UUID.randomUUID(), "IN_PERSON_ID");

        assertThat(verified.getVerifiedBy()).isEqualTo(STAFF);
        assertThat(verified.getRequesterVerifiedAt()).isNotNull();
        assertThat(verified.getState()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("verification with no authenticated user is refused")
    void verificationNeedsAUser() {
        when(requests.findById(any())).thenReturn(Optional.of(request("RECEIVED", false)));
        when(auditor.getCurrentAuditor()).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.verifyRequester(UUID.randomUUID(), "IN_PERSON_ID"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("an unknown verification method is rejected rather than stored")
    void unknownMethodRejected() {
        assertThatThrownBy(() ->
            service.verifyRequester(UUID.randomUUID(), "TRUST_ME"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── Intake ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("raising twice returns the existing open request, not a second one")
    void raiseIsIdempotent() {
        ErasureRequestEntity existing = request("RECEIVED", false);
        when(requests.findOpenFor(PATIENT, "ERASURE")).thenReturn(Optional.of(existing));

        ErasureRequestEntity result = service.raise(PATIENT, "ERASURE", "PORTAL", true, null);

        assertThat(result).isSameAs(existing);
        verify(requests, never()).save(any());
    }

    @Test
    @DisplayName("a new request gets a statutory deadline")
    void raiseSetsDueDate() {
        when(requests.findOpenFor(PATIENT, "ERASURE")).thenReturn(Optional.empty());

        ErasureRequestEntity result = service.raise(PATIENT, "ERASURE", "PORTAL", true, null);

        assertThat(result.getDueAt()).isNotNull();
        assertThat(result.getDueAt()).isAfter(Instant.now().plusSeconds(86_000));
    }

    @Test
    @DisplayName("a CORRECTION with no payload is refused — there is nothing to correct")
    void correctionNeedsPayload() {
        when(requests.findOpenFor(PATIENT, "CORRECTION")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.raise(PATIENT, "CORRECTION", "PORTAL", true, Map.of()))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("an unknown request type is refused")
    void unknownTypeRefused() {
        assertThatThrownBy(() ->
            service.raise(PATIENT, "FORGET_EVERYTHING", "PORTAL", true, null))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── Refusal ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("a refusal without a reason is refused — the patient must be told why")
    void rejectionNeedsReason() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), "  "))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("a refusal reason longer than the column is truncated, not dropped")
    void longRejectionIsTruncated() {
        ErasureRequestEntity r = request("RECEIVED", false);
        when(requests.findById(any())).thenReturn(Optional.of(r));

        ErasureRequestEntity out = service.reject(UUID.randomUUID(), "x".repeat(900));

        assertThat(out.getRejectionReason()).hasSize(500);
    }

    @Test
    @DisplayName("a completed request cannot be refused after the fact")
    void cannotRejectCompleted() {
        when(requests.findById(any())).thenReturn(Optional.of(request("COMPLETED", true)));

        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), "changed our mind"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── The clock ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("overdue requests are counted rather than left to be noticed")
    void overdueIsMetered() {
        ErasureRequestEntity late = request("RECEIVED", false);
        late.setDueAt(Instant.now().minusSeconds(86400));
        when(requests.findOverdue(any())).thenReturn(List.of(late));
        when(requests.countOpen()).thenReturn(1L);

        service.reportOverdue();

        assertThat(meters.find("hms_rights_requests_overdue").gauge()).isNotNull();
        assertThat(meters.find("hms_rights_requests_overdue").gauge().value()).isEqualTo(1.0);
    }
}
