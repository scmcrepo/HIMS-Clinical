package com.hms.application.grievance;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.grievance.ComplianceContactEntity;
import com.hms.infrastructure.persistence.grievance.ComplianceContactJpaRepository;
import com.hms.infrastructure.persistence.grievance.GrievanceEntity;
import com.hms.infrastructure.persistence.grievance.GrievanceEventEntity;
import com.hms.infrastructure.persistence.grievance.GrievanceEventJpaRepository;
import com.hms.infrastructure.persistence.grievance.GrievanceJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;

import java.time.Duration;
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
 * Grievance redressal — WO-027.
 *
 * <p>The Act asks for an <em>effective</em> mechanism, not a reachable one. Most
 * of these assert the difference: that a complaint cannot be resolved without
 * telling anyone anything, that the internal target is kept distinct from the
 * statutory ceiling, and that someone who cannot be matched to a patient record
 * can still complain.
 */
class GrievanceServiceTest {

    private GrievanceJpaRepository grievances;
    private GrievanceEventJpaRepository events;
    private ComplianceContactJpaRepository contacts;
    private MeterRegistry meters;
    private GrievanceService service;

    private static final UUID PATIENT = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        grievances = mock(GrievanceJpaRepository.class);
        events = mock(GrievanceEventJpaRepository.class);
        contacts = mock(ComplianceContactJpaRepository.class);
        AuditorAware<UUID> auditor = mock(AuditorAware.class);
        meters = new SimpleMeterRegistry();
        service = new GrievanceService(grievances, events, contacts, auditor, meters);

        when(grievances.save(any(GrievanceEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(events.save(any(GrievanceEventEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(contacts.save(any(ComplianceContactEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(grievances.findByGrievanceRef(any())).thenReturn(Optional.empty());
        when(contacts.findFirstByActiveToIsNull()).thenReturn(Optional.empty());
    }

    private GrievanceEntity open() {
        GrievanceEntity g = new GrievanceEntity();
        g.setGrievanceRef("GRV-20260830-1111");
        g.setCategory("ERASURE");
        g.setChannel("PORTAL");
        g.setSubject("subject");
        g.setState("RECEIVED");
        g.setReceivedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        g.setTargetAt(Instant.now().plus(28, ChronoUnit.DAYS));
        g.setDueAt(Instant.now().plus(88, ChronoUnit.DAYS));
        return g;
    }

    // ── Intake ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Someone we cannot match to a patient record can still complain")
    void unmatchedComplainantIsAccepted() {
        // Refusing to log a complaint until the person is matched would be a
        // tidy way of never recording the inconvenient ones — and someone
        // complaining that you hold their data wrongly may well not appear the
        // way you expect.
        GrievanceEntity g = service.raise(
            null, "someone@example.com", "DATA_ACCURACY", "EMAIL", "Wrong details", "body");

        assertThat(g.getPatientId()).isNull();
        assertThat(g.getComplainantContact()).isEqualTo("someone@example.com");
    }

    @Test
    @DisplayName("A complaint with no patient and no contact is refused")
    void unreachableComplainantIsRefused() {
        assertThatThrownBy(() ->
            service.raise(null, "  ", "SERVICE", "PHONE", "subject", "body"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("no way to tell the complainant");
    }

    @Test
    @DisplayName("The internal target is set well before the statutory ceiling")
    void targetIsEarlierThanStatutoryDeadline() {
        GrievanceEntity g = service.raise(
            PATIENT, null, "CONSENT", "PORTAL", "subject", "body");

        // A complaint answered on day 89 is compliant and is also a bad outcome.
        // Keeping the two dates apart is what stops 90 days becoming the norm.
        assertThat(g.getTargetAt()).isBefore(g.getDueAt());
        assertThat(Duration.between(g.getReceivedAt(), g.getDueAt()).toDays()).isEqualTo(90);
        assertThat(Duration.between(g.getReceivedAt(), g.getTargetAt()).toDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("Raising records a RECEIVED event, so the timeline starts immediately")
    void raisingRecordsAnEvent() {
        service.raise(PATIENT, null, "CONSENT", "PORTAL", "subject", "body");

        org.mockito.ArgumentCaptor<GrievanceEventEntity> captor =
            org.mockito.ArgumentCaptor.forClass(GrievanceEventEntity.class);
        org.mockito.Mockito.verify(events).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("RECEIVED");
    }

    // ── Acknowledgement ───────────────────────────────────────────────────

    @Test
    @DisplayName("Acknowledgement is separate from resolution and is idempotent")
    void acknowledgementIsIdempotent() {
        GrievanceEntity g = open();
        when(grievances.findById(any())).thenReturn(Optional.of(g));

        service.acknowledge(UUID.randomUUID(), "We have your complaint");
        Instant first = g.getAcknowledgedAt();
        service.acknowledge(UUID.randomUUID(), "again");

        assertThat(g.getAcknowledgedAt()).isEqualTo(first);
        assertThat(g.getState()).isEqualTo("ACKNOWLEDGED");
    }

    // ── Resolution ────────────────────────────────────────────────────────

    @Test
    @DisplayName("A resolution with no text is refused")
    void resolutionRequiresText() {
        when(grievances.findById(any())).thenReturn(Optional.of(open()));

        // A status change dressed up as an answer is indistinguishable, from the
        // complainant's side, from being ignored.
        assertThatThrownBy(() -> service.resolve(UUID.randomUUID(), "   "))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("Resolving within the window is counted as within target and within statutory")
    void timelyResolutionIsCounted() {
        GrievanceEntity g = open();
        when(grievances.findById(any())).thenReturn(Optional.of(g));

        service.resolve(UUID.randomUUID(), "We corrected the record.");

        assertThat(g.getState()).isEqualTo("RESOLVED");
        assertThat(meters.counter("hms_grievance_resolutions_total",
                                  "within_target", "true",
                                  "within_statutory", "true").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("A late resolution is counted separately rather than looking the same")
    void lateResolutionIsDistinguished() {
        GrievanceEntity g = open();
        g.setTargetAt(Instant.now().minus(60, ChronoUnit.DAYS));
        g.setDueAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(grievances.findById(any())).thenReturn(Optional.of(g));

        service.resolve(UUID.randomUUID(), "Late answer.");

        assertThat(meters.counter("hms_grievance_resolutions_total",
                                  "within_target", "false",
                                  "within_statutory", "false").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("An already-resolved grievance cannot be resolved again")
    void cannotResolveTwice() {
        GrievanceEntity g = open();
        g.setState("RESOLVED");
        when(grievances.findById(any())).thenReturn(Optional.of(g));

        assertThatThrownBy(() -> service.resolve(UUID.randomUUID(), "again"))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── Escalation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Escalation to the Board is recorded, not treated as an error")
    void escalationIsRecorded() {
        GrievanceEntity g = open();
        when(grievances.findById(any())).thenReturn(Optional.of(g));

        // The complainant is entitled to go to the Board at any point. A
        // mechanism that flagged this as a failure would measure the wrong thing.
        service.recordEscalation(UUID.randomUUID(), "DPB-2026-0042");

        assertThat(g.isEscalatedToBoard()).isTrue();
        assertThat(g.getBoardReference()).isEqualTo("DPB-2026-0042");
        assertThat(g.isOpen()).isTrue();
    }

    // ── The published contact ─────────────────────────────────────────────

    @Test
    @DisplayName("A DPO who is not based in India is refused")
    void dpoMustBeInIndia() {
        // Rule 13 requires an SDF's DPO to be India-based. Rejected here so it
        // surfaces at the point of entry rather than during an audit.
        assertThatThrownBy(() -> service.publishContact(
                "A. Person", "DPO", "dpo@example.com", null, null, true, false))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("India");
    }

    @Test
    @DisplayName("A non-DPO contact outside India is allowed")
    void nonDpoContactNeedNotBeInIndia() {
        assertThat(service.publishContact(
            "Support", "Privacy contact", "privacy@example.com", null, null, false, false))
            .isNotNull();
    }

    @Test
    @DisplayName("Publishing a new contact supersedes the old one rather than overwriting it")
    void publishingSupersedes() {
        ComplianceContactEntity existing = new ComplianceContactEntity();
        existing.setDisplayName("Old contact");
        existing.setEmail("old@example.com");
        when(contacts.findFirstByActiveToIsNull()).thenReturn(Optional.of(existing));

        service.publishContact("New contact", "DPO", "new@example.com", null, null, true, true);

        // The address published last year is what a complainant from last year
        // was told to use; overwriting in place would erase that record.
        assertThat(existing.getActiveTo()).isNotNull();
    }

    // ── SLA reporting ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Overdue, past-target and unacknowledged are counted separately")
    void slaGaugesArePublished() {
        when(grievances.findOverdue(any())).thenReturn(List.of(open()));
        when(grievances.findPastTarget(any())).thenReturn(List.of(open(), open()));
        when(grievances.findUnacknowledged(any())).thenReturn(List.of(open()));
        when(grievances.countOpen()).thenReturn(4L);
        when(grievances.countEscalated()).thenReturn(1L);

        service.reportSlaBreaches();

        assertThat(meters.find("hms_grievances_overdue").gauge().value()).isEqualTo(1.0);
        assertThat(meters.find("hms_grievances_past_target").gauge().value()).isEqualTo(2.0);
        assertThat(meters.find("hms_grievances_unacknowledged").gauge().value()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("isOverdue only applies to open grievances")
    void resolvedGrievancesAreNotOverdue() {
        GrievanceEntity g = open();
        g.setDueAt(Instant.now().minus(10, ChronoUnit.DAYS));
        assertThat(g.isOverdue(Instant.now())).isTrue();

        g.setState("RESOLVED");
        assertThat(g.isOverdue(Instant.now())).isFalse();
    }
}
