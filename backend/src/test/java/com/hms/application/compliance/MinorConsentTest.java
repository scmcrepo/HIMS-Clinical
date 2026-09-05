package com.hms.application.compliance;

import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.compliance.ConsentNoticeJpaRepository;
import com.hms.infrastructure.persistence.compliance.ConsentRecordEntity;
import com.hms.infrastructure.persistence.compliance.ConsentRecordJpaRepository;
import com.hms.infrastructure.persistence.patient.PatientDobView;
import com.hms.infrastructure.persistence.patient.PatientJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WO-032 / F3 — DPDP s. 9, minority derived from the record rather than declared
 * on a form.
 *
 * <h2>The defect these cover</h2>
 * {@code ConsentService.grant} already refused a minor's consent without verified
 * guardian approval, and that check was correct. But {@code minor} arrived as a
 * boolean on the request body, defaulting to {@code false}, and nothing compared
 * it to the patient's date of birth.
 *
 * <p>So the s. 9 control was only ever as strong as whoever ticked the box at the
 * front desk, and it failed towards "adult": a paediatric patient whose form was
 * left unticked produced a consent record that looks completely clean in an
 * audit. Same shape as the self-granting defect WO-022 fixed — a record
 * asserting something nobody checked.
 *
 * <p>The most important test here is {@link #unknownDobIsNotTreatedAsAdult}.
 * Collapsing "we don't know" into "adult" is precisely how the original bug
 * behaved, and it is the easy thing to do when someone later simplifies this
 * code.
 */
@DisplayName("Consent — minority is derived from date of birth, not declared")
class MinorConsentTest {

    private ConsentRecordJpaRepository records;
    private ConsentNoticeJpaRepository notices;
    private MeterRegistry meters;
    private MinorDetermination minors;
    private ConsentService service;

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        records = mock(ConsentRecordJpaRepository.class);
        notices = mock(ConsentNoticeJpaRepository.class);
        meters = new SimpleMeterRegistry();
        minors = mock(MinorDetermination.class);
        service = new ConsentService(records, notices, meters, minors);
        ReflectionTestUtils.setField(service, "enforcementMode", "enforce");

        when(records.findByPatientIdAndPurposeAndState(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(records.save(any(ConsentRecordEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private ConsentRecordEntity grant(boolean claimedMinor, boolean guardianVerified) {
        return service.grant(PATIENT, ConsentPurpose.TREATMENT, "v2.0-draft", "en",
                             "notice body", "IN_PERSON", STAFF,
                             claimedMinor, guardianVerified, null,
                             ConsentProvenance.STAFF_ATTESTED);
    }

    @Test
    @DisplayName("an adult attested as an adult is recorded as an adult")
    void adultClaimedAsAdultIsAccepted() {
        when(minors.isMinor(PATIENT)).thenReturn(Optional.of(false));

        ConsentRecordEntity saved = grant(false, false);

        assertThat(saved.isMinor()).isFalse();
    }

    @Test
    @DisplayName("a child attested as an adult is refused, not silently corrected")
    void childClaimedAsAdultIsRefused() {
        when(minors.isMinor(PATIENT)).thenReturn(Optional.of(true));

        assertThatThrownBy(() -> grant(false, false))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("date of");

        // The point of refusing rather than correcting: nothing is written. A
        // record silently flipped to minor=true would claim the desk attested to
        // something it did not.
        verify(records, never()).save(any(ConsentRecordEntity.class));

        assertThat(meters.counter("hms_consent_minor_dob_mismatch_total",
                                  "purpose", "TREATMENT", "claimed", "false").count())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("an adult attested as a child is also refused — the check runs both ways")
    void adultClaimedAsChildIsRefused() {
        when(minors.isMinor(PATIENT)).thenReturn(Optional.of(false));

        assertThatThrownBy(() -> grant(true, true))
            .isInstanceOf(BusinessRuleViolationException.class);

        verify(records, never()).save(any(ConsentRecordEntity.class));
    }

    @Test
    @DisplayName("a child with verified guardian approval is recorded as a minor")
    void childWithGuardianIsAccepted() {
        when(minors.isMinor(PATIENT)).thenReturn(Optional.of(true));

        ConsentRecordEntity saved = grant(true, true);

        assertThat(saved.isMinor()).isTrue();
        assertThat(saved.isGuardianVerified()).isTrue();
    }

    @Test
    @DisplayName("a child without verified guardian approval is refused — s. 9")
    void childWithoutGuardianIsRefused() {
        when(minors.isMinor(PATIENT)).thenReturn(Optional.of(true));

        assertThatThrownBy(() -> grant(true, false))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("guardian");
    }

    @Test
    @DisplayName("no date of birth on file leaves minority undetermined, and counted")
    void unknownDobIsNotTreatedAsAdult() {
        when(minors.isMinor(PATIENT)).thenReturn(Optional.empty());

        // The attestation is all there is, so it stands — but the fact that it
        // could not be checked is recorded. Treating unknown as "adult" is the
        // original defect; treating it as "child" would block every patient
        // registered without a date of birth.
        ConsentRecordEntity saved = grant(true, true);
        assertThat(saved.isMinor()).isTrue();

        assertThat(meters.counter("hms_consent_minor_undetermined_total",
                                  "purpose", "TREATMENT").count())
            .isEqualTo(1.0);
        assertThat(meters.find("hms_consent_minor_dob_mismatch_total").counter())
            .as("nothing to mismatch against when the DOB is unknown")
            .isNull();
    }

    // ── MinorDetermination itself ────────────────────────────────────────────

    @Test
    @DisplayName("age is measured against eighteen, using the estimate when there is no real DOB")
    void determinationReadsBothDateColumns() {
        PatientJpaRepository patients = mock(PatientJpaRepository.class);
        MinorDetermination determination = new MinorDetermination(patients);

        when(patients.findDobById(PATIENT)).thenReturn(Optional.of(
            dob(LocalDate.now().minusYears(10), null)));
        assertThat(determination.isMinor(PATIENT)).contains(true);

        when(patients.findDobById(PATIENT)).thenReturn(Optional.of(
            dob(null, LocalDate.now().minusYears(40))));
        assertThat(determination.isMinor(PATIENT))
            .as("the estimate is used when no exact date was recorded — a large "
                + "share of paediatric registrations have only an estimate")
            .contains(false);

        when(patients.findDobById(PATIENT)).thenReturn(Optional.of(dob(null, null)));
        assertThat(determination.isMinor(PATIENT))
            .as("no date at all is unknown, not adult")
            .isEmpty();

        when(patients.findDobById(PATIENT)).thenReturn(Optional.of(
            dob(LocalDate.now().plusYears(1), null)));
        assertThat(determination.isMinor(PATIENT))
            .as("a future birth date is a data-entry error, not a negative age")
            .isEmpty();

        when(patients.findDobById(PATIENT)).thenReturn(Optional.empty());
        assertThat(determination.isMinor(PATIENT))
            .as("a patient outside the current tenant is unknown, not adult")
            .isEmpty();
    }

    @Test
    @DisplayName("exactly eighteen today is an adult")
    void eighteenthBirthdayIsMajority() {
        PatientJpaRepository patients = mock(PatientJpaRepository.class);
        MinorDetermination determination = new MinorDetermination(patients);

        when(patients.findDobById(PATIENT)).thenReturn(Optional.of(
            dob(LocalDate.now().minusYears(18), null)));
        assertThat(determination.isMinor(PATIENT)).contains(false);

        when(patients.findDobById(PATIENT)).thenReturn(Optional.of(
            dob(LocalDate.now().minusYears(18).plusDays(1), null)));
        assertThat(determination.isMinor(PATIENT))
            .as("one day short of eighteen is still a child")
            .contains(true);
    }

    private static PatientDobView dob(LocalDate exact, LocalDate estimated) {
        return new PatientDobView() {
            @Override public LocalDate getDateOfBirth() { return exact; }
            @Override public LocalDate getEstimatedDateOfBirth() { return estimated; }
        };
    }
}
