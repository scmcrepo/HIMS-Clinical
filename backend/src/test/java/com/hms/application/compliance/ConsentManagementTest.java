package com.hms.application.compliance;

import com.hms.infrastructure.persistence.compliance.ConsentNoticeEntity;
import com.hms.infrastructure.persistence.compliance.ConsentNoticeJpaRepository;
import com.hms.infrastructure.persistence.compliance.ConsentRecordEntity;
import com.hms.infrastructure.persistence.compliance.ConsentRecordJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consent management surface — WO-023.
 *
 * <p>Two things get asserted here that are easy to lose. Withdrawal must stay
 * cheap, because consent harder to withdraw than to give is not freely given.
 * And every purpose must have notice text, because a purpose without one throws
 * at the moment a patient is standing at the desk.
 */
class ConsentManagementTest {

    private ConsentRecordJpaRepository records;
    private ConsentNoticeJpaRepository notices;
    private MeterRegistry meters;
    private ConsentService service;

    private static final UUID PATIENT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        records = mock(ConsentRecordJpaRepository.class);
        notices = mock(ConsentNoticeJpaRepository.class);
        meters = new SimpleMeterRegistry();
        service = new ConsentService(records, notices, meters);
        ReflectionTestUtils.setField(service, "enforcementMode", "enforce");
        when(records.save(any(ConsentRecordEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ConsentRecordEntity granted(ConsentPurpose purpose) {
        ConsentRecordEntity r = new ConsentRecordEntity();
        r.setPatientId(PATIENT);
        r.setPurpose(purpose.name());
        r.setState("GRANTED");
        r.setProvenance(ConsentProvenance.STAFF_ATTESTED.name());
        r.setGrantedAt(Instant.now().minusSeconds(60));
        return r;
    }

    // ── Withdrawal ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Withdrawal marks the record, never deletes it")
    void withdrawalPreservesTheRecord() {
        ConsentRecordEntity record = granted(ConsentPurpose.AGENT_VOICE);
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "AGENT_VOICE", "GRANTED"))
            .thenReturn(Optional.of(record));

        service.withdraw(PATIENT, ConsentPurpose.AGENT_VOICE, "PORTAL", STAFF);

        assertThat(record.getState()).isEqualTo("WITHDRAWN");
        assertThat(record.getWithdrawnAt()).isNotNull();
        // The row is the evidence consent existed and was revoked. Deleting it
        // would destroy exactly the trail the Act requires.
        verify(records).save(record);
        verify(records, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    @DisplayName("Withdrawing twice is not an error — a patient pressing stop again is not a fault")
    void withdrawalIsIdempotent() {
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "AGENT_VOICE", "GRANTED"))
            .thenReturn(Optional.empty());

        service.withdraw(PATIENT, ConsentPurpose.AGENT_VOICE, "PORTAL", STAFF);
        // No exception, no write.
        verify(records, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("After withdrawal the gate refuses again")
    void withdrawalRevokesAccess() {
        when(records.findByPatientIdAndPurposeAndState(PATIENT, "AGENT_VOICE", "GRANTED"))
            .thenReturn(Optional.empty());

        assertThat(service.hasConsent(PATIENT, ConsentPurpose.AGENT_VOICE)).isFalse();
    }

    // ── PORTAL_SELF_ACCESS ────────────────────────────────────────────────

    @Test
    @DisplayName("Portal self-registration records PATIENT_DIGITAL consent with no capturer")
    void portalConsentIsPatientDigital() {
        when(records.findByPatientIdAndPurposeAndState(any(), any(), any()))
            .thenReturn(Optional.empty());

        ConsentRecordEntity saved = service.grant(
            PATIENT, ConsentPurpose.PORTAL_SELF_ACCESS, "v1.0", "en",
            "Viewing your own records", "PORTAL", null, false, false, null,
            ConsentProvenance.PATIENT_DIGITAL);

        // capturedBy is legitimately null: the patient ticked the box themselves
        // and no staff member attested to anything. Only STAFF_ATTESTED requires
        // a capturer.
        assertThat(saved.getCapturedBy()).isNull();
        assertThat(saved.getProvenance()).isEqualTo(ConsentProvenance.PATIENT_DIGITAL.name());
        assertThat(ConsentProvenance.PATIENT_DIGITAL.isReliable()).isTrue();
    }

    @Test
    @DisplayName("PORTAL_SELF_ACCESS is a real purpose, not just a string in PortalProperties")
    void portalPurposeExists() {
        // It was referenced from PortalProperties for months while not being a
        // member of this enum, which is why registration consent went nowhere.
        assertThat(ConsentPurpose.valueOf("PORTAL_SELF_ACCESS")).isNotNull();
        assertThat(ConsentPurpose.PORTAL_SELF_ACCESS.isRequiredForCare()).isFalse();
    }

    // ── Notice coverage ───────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(ConsentPurpose.class)
    @DisplayName("Every purpose has seeded notice text across V205 and V207")
    void everyPurposeHasNoticeText(ConsentPurpose purpose) throws IOException {
        // A purpose with no seeded notice throws at the moment a patient is
        // standing at the desk, which is the worst possible time to find out.
        Path dir = Paths.get("src/main/resources/db/migration");
        String seeds = Files.readString(dir.resolve("V205__consent_provenance_and_notices.sql"))
                     + Files.readString(dir.resolve("V207__consent_management_surface.sql"));

        assertThat(seeds)
            .as("No notice seeded for %s. Add it to V207 or a later migration.", purpose)
            .contains("'" + purpose.name() + "'");
    }

    @Test
    @DisplayName("Only TREATMENT blocks care — everything else must be genuinely optional")
    void onlyTreatmentIsRequired() {
        // Consent conditioned on receiving treatment is not freely given, so if
        // this list ever grows it is a compliance decision, not a code change.
        List<ConsentPurpose> required = java.util.Arrays.stream(ConsentPurpose.values())
            .filter(ConsentPurpose::isRequiredForCare)
            .toList();

        assertThat(required).containsExactly(ConsentPurpose.TREATMENT);
    }

    @Test
    @DisplayName("An ACTIVE notice wins over a DRAFT placeholder without a code change")
    void activeNoticeBeatsDraft() {
        ConsentNoticeEntity draft = new ConsentNoticeEntity();
        draft.setPurpose("TREATMENT");
        draft.setVersion("v1.0");
        draft.setLanguage("en");
        draft.setBodyText("placeholder");
        draft.setNoticeState("DRAFT");
        draft.setEffectiveFrom(Instant.now().minusSeconds(600));

        ConsentNoticeEntity active = new ConsentNoticeEntity();
        active.setPurpose("TREATMENT");
        active.setVersion("v2.0");
        active.setLanguage("en");
        active.setBodyText("counsel-approved text");
        active.setNoticeState("ACTIVE");
        active.setEffectiveFrom(Instant.now().minusSeconds(60));

        // Repository orders ACTIVE first; this asserts the service honours it.
        when(notices.findCandidates("TREATMENT", "en")).thenReturn(List.of(active, draft));

        assertThat(service.activeNotice(ConsentPurpose.TREATMENT, "en"))
            .get()
            .extracting(ConsentNoticeEntity::getVersion)
            .isEqualTo("v2.0");
    }
}
