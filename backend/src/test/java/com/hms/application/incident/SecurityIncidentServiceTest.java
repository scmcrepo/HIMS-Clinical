package com.hms.application.incident;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.incident.IncidentAffectedPrincipalEntity;
import com.hms.infrastructure.persistence.incident.IncidentAffectedPrincipalJpaRepository;
import com.hms.infrastructure.persistence.incident.SecurityIncidentEntity;
import com.hms.infrastructure.persistence.incident.SecurityIncidentJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Breach notification — WO-026.
 *
 * <p>The theme running through these is that an incident must not be able to
 * look finished while someone is still owed a notification. That is the failure
 * mode with legal consequences: a register saying everyone was told, when they
 * were not.
 */
class SecurityIncidentServiceTest {

    private SecurityIncidentJpaRepository incidents;
    private IncidentAffectedPrincipalJpaRepository affected;
    private MeterRegistry meters;
    private SecurityIncidentService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        incidents = mock(SecurityIncidentJpaRepository.class);
        affected = mock(IncidentAffectedPrincipalJpaRepository.class);
        AuditorAware<UUID> auditor = mock(AuditorAware.class);
        meters = new SimpleMeterRegistry();
        service = new SecurityIncidentService(incidents, affected, auditor, meters);

        when(incidents.save(any(SecurityIncidentEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(affected.save(any(IncidentAffectedPrincipalEntity.class)))
            .thenAnswer(i -> i.getArgument(0));
        when(incidents.findByIncidentRef(any())).thenReturn(Optional.empty());
    }

    private SecurityIncidentEntity incident(String state, int affectedCount) {
        SecurityIncidentEntity i = new SecurityIncidentEntity();
        i.setIncidentRef("INC-20260830-1234");
        i.setCategory("CROSS_TENANT_ACCESS");
        i.setSeverity("HIGH");
        i.setState(state);
        i.setDetectedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        i.setSummary("Test incident");
        i.setAffectedPrincipalCount(affectedCount);
        return i;
    }

    // ── The clocks start when we knew, not when we filed ──────────────────

    @Test
    @DisplayName("A backdated detection time is preserved, not reset to now")
    void detectedAtIsNotSilentlyReset() {
        Instant knownAt = Instant.now().minus(30, ChronoUnit.HOURS);

        SecurityIncidentEntity raised = service.raise(
            "DATA_EXPOSURE", "HIGH", "summary", "detail", "MANUAL_REPORT",
            "contact details", knownAt, false);

        // An incident discovered yesterday and filed today is still a yesterday
        // incident. Resetting this would hand back hours the hospital does not
        // have against a 72-hour obligation.
        assertThat(raised.getDetectedAt()).isEqualTo(knownAt);
    }

    @Test
    @DisplayName("An omitted detection time defaults to now rather than null")
    void detectedAtDefaults() {
        SecurityIncidentEntity raised = service.raise(
            "OTHER", "LOW", "summary", null, "MANUAL_REPORT", null, null, false);

        assertThat(raised.getDetectedAt()).isNotNull();
    }

    // ── Cannot close while anyone is owed a notification ──────────────────

    @Test
    @DisplayName("An incident with affected people cannot close before the Board is told")
    void cannotCloseWithoutBoardNotification() {
        SecurityIncidentEntity i = incident("CONTAINED", 40);
        when(incidents.findById(any())).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.close(UUID.randomUUID(), "root cause"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("notified");
    }

    @Test
    @DisplayName("An incident cannot close before the affected people are told")
    void cannotCloseWithoutPrincipalNotification() {
        SecurityIncidentEntity i = incident("CONTAINED", 40);
        i.setBoardNotifiedAt(Instant.now());
        when(incidents.findById(any())).thenReturn(Optional.of(i));

        assertThatThrownBy(() -> service.close(UUID.randomUUID(), "root cause"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("An incident affecting nobody can close without notifications")
    void canCloseWithNoAffectedPeople() {
        SecurityIncidentEntity i = incident("CONTAINED", 0);
        when(incidents.findById(any())).thenReturn(Optional.of(i));

        assertThat(service.close(UUID.randomUUID(), "no data disclosed").getState())
            .isEqualTo("CLOSED");
    }

    // ── A failed notification keeps the incident open ─────────────────────

    @Test
    @DisplayName("Undelivered notifications leave principalsNotifiedAt unset")
    void failedNotificationsBlockCompletion() {
        SecurityIncidentEntity i = incident("CONTAINED", 3);
        when(incidents.findById(any())).thenReturn(Optional.of(i));
        when(affected.findByIncidentIdAndNotificationState(any(), eqPending()))
            .thenReturn(List.of(new IncidentAffectedPrincipalEntity()));
        when(affected.countByIncidentIdAndNotificationState(any(), eqFailed())).thenReturn(2L);

        service.recordPrincipalNotifications(UUID.randomUUID(), "SMS");

        // Left null on purpose: the DB CHECK then prevents closure. "We sent a
        // notice" and "everyone received one" are different claims, and only
        // the second discharges the duty.
        assertThat(i.getPrincipalsNotifiedAt()).isNull();
    }

    @Test
    @DisplayName("With everyone reached, the incident moves to NOTIFIED")
    void completeNotificationAdvancesState() {
        SecurityIncidentEntity i = incident("CONTAINED", 1);
        when(incidents.findById(any())).thenReturn(Optional.of(i));
        when(affected.findByIncidentIdAndNotificationState(any(), eqPending()))
            .thenReturn(List.of(new IncidentAffectedPrincipalEntity()));
        when(affected.countByIncidentIdAndNotificationState(any(), eqFailed())).thenReturn(0L);

        service.recordPrincipalNotifications(UUID.randomUUID(), "SMS");

        assertThat(i.getPrincipalsNotifiedAt()).isNotNull();
        assertThat(i.getState()).isEqualTo("NOTIFIED");
    }

    private static String eqPending() { return org.mockito.ArgumentMatchers.eq("PENDING"); }
    private static String eqFailed()  { return org.mockito.ArgumentMatchers.eq("FAILED"); }

    // ── Board reporting order ─────────────────────────────────────────────

    @Test
    @DisplayName("The 72-hour detailed report cannot precede the initial intimation")
    void detailReportRequiresInitial() {
        SecurityIncidentEntity i = incident("CONTAINED", 5);
        when(incidents.findById(any())).thenReturn(Optional.of(i));

        assertThatThrownBy(() ->
            service.recordBoardNotification(UUID.randomUUID(), "REF-1", true))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("initial");
    }

    // ── The notice ────────────────────────────────────────────────────────

    @Test
    @DisplayName("The notice carries all four things Rule 7 requires")
    void noticeCoversRule7Elements() {
        SecurityIncidentEntity i = incident("CONTAINED", 5);
        i.setDataCategories("Name and contact number");
        i.setRemediation("We closed the affected endpoint.");
        when(incidents.findById(any())).thenReturn(Optional.of(i));

        String notice = service.draftPrincipalNotice(UUID.randomUUID(), "dpo@hospital.example");

        assertThat(notice).contains("What happened");          // nature
        assertThat(notice).contains("What this could mean");    // consequences
        assertThat(notice).contains("What we are doing");       // remedial measures
        assertThat(notice).contains("dpo@hospital.example");    // contact
        assertThat(notice).contains(i.getIncidentRef());
        // Route of complaint, so the notice does not read as the last word.
        assertThat(notice).contains("Data Protection Board");
    }

    @Test
    @DisplayName("An uncertain scope is stated plainly rather than glossed over")
    void uncertainScopeIsDisclosed() {
        SecurityIncidentEntity i = incident("OPEN", 5);
        i.setScopeUncertain(true);
        when(incidents.findById(any())).thenReturn(Optional.of(i));

        // A person told the scope is unknown can protect themselves; one falsely
        // reassured cannot.
        assertThat(service.draftPrincipalNotice(UUID.randomUUID(), "x@y.example"))
            .contains("still establishing");
    }

    @Test
    @DisplayName("Dismissing without a reason is refused")
    void dismissalNeedsReason() {
        assertThatThrownBy(() -> service.dismiss(UUID.randomUUID(), "  "))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("Incident references are unique-ish and human-readable")
    void referenceIsReadable() {
        SecurityIncidentEntity raised = service.raise(
            "OTHER", "LOW", "s", null, "MANUAL_REPORT", null, null, false);

        // Random suffix, not a sequence: a monotonic counter would tell anyone
        // holding one reference how many incidents there have been.
        assertThat(raised.getIncidentRef()).startsWith("INC-").hasSizeGreaterThan(12);
    }
}
