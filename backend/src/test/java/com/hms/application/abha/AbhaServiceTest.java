package com.hms.application.abha;

import com.hms.api.abha.response.AbhaLinkageResponse;
import com.hms.application.compliance.ConsentPurpose;
import com.hms.application.compliance.ConsentRequiredException;
import com.hms.application.compliance.ConsentService;
import com.hms.application.compliance.PiiDisclosureAuditService;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.abdm.AbdmClient;
import com.hms.infrastructure.persistence.abha.AbhaLinkageEntity;
import com.hms.infrastructure.persistence.abha.AbhaLinkageJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WO-012 / AB-001.
 *
 * <p>Three properties are worth more than the rest here, and they are the ones a
 * green build would otherwise lie about: Aadhaar must never be persisted, the
 * DPDP consent gate must fire <em>before</em> the gateway is called, and the
 * ABHA number must leave the API masked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbhaServiceTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final String AADHAAR = "123456789012";
    private static final String ABHA_NUMBER = "91234567890123";

    @Mock private AbdmClient abdm;
    @Mock private AbhaLinkageJpaRepository repository;
    @Mock private PiiSearchTokenService searchTokens;
    @Mock private ConsentService consent;
    @Mock private PiiDisclosureAuditService disclosureAudit;

    private AbhaService service;

    @BeforeEach
    void setUp() {
        service = new AbhaService(abdm, repository, searchTokens, consent, disclosureAudit,
                                  new SimpleMeterRegistry());
        when(repository.save(any(AbhaLinkageEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(searchTokens.token(anyString())).thenAnswer(inv -> "tok:" + inv.getArgument(0));
    }

    // ── consent gate ─────────────────────────────────────────────────────────

    @Test
    void refusesEnrolmentWithoutDpdpConsent() {
        doThrow(new ConsentRequiredException(ConsentPurpose.ABHA_LINKAGE))
            .when(consent).requireConsent(PATIENT, ConsentPurpose.ABHA_LINKAGE);

        assertThrows(ConsentRequiredException.class,
            () -> service.startEnrolment(PATIENT, AbhaService.OtpChannel.AADHAAR, AADHAAR));

        // The gateway must not be touched before consent is established.
        verify(abdm, never()).requestAadhaarOtp(anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void requiresAbhaLinkageConsentSpecificallyNotTreatmentConsent() {
        when(abdm.requestAadhaarOtp(AADHAAR))
            .thenReturn(new AbdmClient.OtpChallenge("txn-1", "XXXXXX7890"));

        service.startEnrolment(PATIENT, AbhaService.OtpChannel.AADHAAR, AADHAAR);

        verify(consent).requireConsent(PATIENT, ConsentPurpose.ABHA_LINKAGE);
        verify(consent, never()).requireConsent(PATIENT, ConsentPurpose.TREATMENT);
    }

    // ── Aadhaar must not be persisted ────────────────────────────────────────

    @Test
    void aadhaarIsNeverWrittenToTheLinkageRow() {
        when(abdm.requestAadhaarOtp(AADHAAR))
            .thenReturn(new AbdmClient.OtpChallenge("txn-1", "XXXXXX7890"));

        service.startEnrolment(PATIENT, AbhaService.OtpChannel.AADHAAR, AADHAAR);

        ArgumentCaptor<AbhaLinkageEntity> saved =
            ArgumentCaptor.forClass(AbhaLinkageEntity.class);
        verify(repository).save(saved.capture());

        AbhaLinkageEntity row = saved.getValue();
        assertFalse(AADHAAR.equals(row.getAbhaNumber()));
        assertFalse(AADHAAR.equals(row.getAbhaAddress()));
        assertFalse(AADHAAR.equals(row.getTransactionId()));
        assertFalse(AADHAAR.equals(row.getAbhaNumberToken()));
        assertEquals(AbhaService.STATE_PENDING_OTP, row.getLinkageState());
    }

    @Test
    void recordsDpdpConsentTimestampOnTheLinkage() {
        when(abdm.requestMobileOtp("9876543210"))
            .thenReturn(new AbdmClient.OtpChallenge("txn-2", "XXXXXX3210"));

        AbhaLinkageEntity row =
            service.startEnrolment(PATIENT, AbhaService.OtpChannel.MOBILE, "9876543210");

        assertNotNull(row.getConsentRecordedAt());
        assertEquals(ConsentPurpose.ABHA_LINKAGE.name(), row.getConsentVersion());
    }

    // ── duplicate identity ───────────────────────────────────────────────────

    @Test
    void refusesSecondEnrolmentWhenPatientAlreadyLinked() {
        AbhaLinkageEntity linked = new AbhaLinkageEntity();
        linked.setLinkageState(AbhaService.STATE_LINKED);
        when(repository.findByPatientIdAndLinkageState(PATIENT, AbhaService.STATE_LINKED))
            .thenReturn(Optional.of(linked));

        assertThrows(BusinessRuleViolationException.class,
            () -> service.startEnrolment(PATIENT, AbhaService.OtpChannel.AADHAAR, AADHAAR));

        verify(abdm, never()).requestAadhaarOtp(anyString());
    }

    // ── verification ─────────────────────────────────────────────────────────

    @Test
    void verificationStoresBlindIndexTokensBesideEncryptedValues() {
        AbhaLinkageEntity pending = pendingLinkage();
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(abdm.verifyOtpAndEnrol("txn-1", "123456", "9876543210"))
            .thenReturn(new AbdmClient.AbhaIdentity(ABHA_NUMBER, "ravi@abdm", "txn-1"));

        AbhaLinkageEntity row = service.verifyOtp(pending.getId(), "123456", "9876543210");

        assertEquals(ABHA_NUMBER, row.getAbhaNumber());
        assertEquals("tok:" + ABHA_NUMBER, row.getAbhaNumberToken());
        assertEquals("tok:ravi@abdm", row.getAbhaAddressToken());
        assertEquals(AbhaService.STATE_LINKED, row.getLinkageState());
        assertNotNull(row.getLinkedAt());
    }

    @Test
    void verificationRejectsAnEnrolmentThatIsNotAwaitingOtp() {
        AbhaLinkageEntity done = pendingLinkage();
        done.setLinkageState(AbhaService.STATE_LINKED);
        when(repository.findById(done.getId())).thenReturn(Optional.of(done));

        assertThrows(BusinessRuleViolationException.class,
            () -> service.verifyOtp(done.getId(), "123456", null));
        verify(abdm, never()).verifyOtpAndEnrol(anyString(), anyString(), any());
    }

    @Test
    void gatewayFailureIsRecordedAsTypeNameNotAsMessage() {
        AbhaLinkageEntity pending = pendingLinkage();
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        // An ABDM error body can echo the submitted Aadhaar, so the message must
        // not reach the database.
        when(abdm.verifyOtpAndEnrol(anyString(), anyString(), any()))
            .thenThrow(new IllegalStateException("rejected for aadhaar " + AADHAAR));

        assertThrows(IllegalStateException.class,
            () -> service.verifyOtp(pending.getId(), "999999", null));

        assertEquals(AbhaService.STATE_FAILED, pending.getLinkageState());
        assertEquals("IllegalStateException", pending.getFailureCode());
        assertFalse(pending.getFailureCode().contains(AADHAAR));
    }

    @Test
    void missingAbhaNumberFromGatewayIsTreatedAsFailure() {
        AbhaLinkageEntity pending = pendingLinkage();
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(abdm.verifyOtpAndEnrol(anyString(), anyString(), any()))
            .thenReturn(new AbdmClient.AbhaIdentity(null, "ravi@abdm", "txn-1"));

        assertThrows(BusinessRuleViolationException.class,
            () -> service.verifyOtp(pending.getId(), "123456", null));
        assertEquals(AbhaService.STATE_FAILED, pending.getLinkageState());
    }

    // ── masking ──────────────────────────────────────────────────────────────

    @Test
    void responseMasksAllButTheLastFourDigits() {
        AbhaLinkageEntity row = pendingLinkage();
        row.setAbhaNumber(ABHA_NUMBER);
        row.setLinkageState(AbhaService.STATE_LINKED);

        AbhaLinkageResponse dto = AbhaLinkageResponse.from(row);

        assertEquals("XX-XXXX-XXXX-0123", dto.abhaNumberMasked());
        assertFalse(dto.abhaNumberMasked().contains("9123456789"));
    }

    @Test
    void responseMaskIsNullWhenNoAbhaNumberYet() {
        assertNull(AbhaLinkageResponse.from(pendingLinkage()).abhaNumberMasked());
    }

    @Test
    void addressAvailabilityInvertsTheGatewayExistenceCheck() {
        when(abdm.abhaAddressExists("taken@abdm")).thenReturn(true);
        when(abdm.abhaAddressExists("free@abdm")).thenReturn(false);

        assertFalse(service.abhaAddressAvailable("taken@abdm"));
        assertTrue(service.abhaAddressAvailable("free@abdm"));
    }

    // ── card download (AB-004) ───────────────────────────────────────────────

    @Test
    void cardDownloadIsAuditedOnSuccess() {
        AbhaLinkageEntity linked = pendingLinkage();
        linked.setLinkageState(AbhaService.STATE_LINKED);
        linked.setAbhaNumber(ABHA_NUMBER);
        UUID actor = UUID.randomUUID();
        when(repository.findByPatientIdAndLinkageState(PATIENT, AbhaService.STATE_LINKED))
            .thenReturn(Optional.of(linked));
        when(abdm.fetchAbhaCard(ABHA_NUMBER)).thenReturn(new byte[]{1, 2, 3});

        byte[] card = service.downloadCard(PATIENT, actor, "patient requested a copy");

        assertEquals(3, card.length);
        verify(disclosureAudit).recordSuccess(PiiDisclosureAuditService.ABHA_CARD,
                                              PATIENT, linked.getId(), actor,
                                              "patient requested a copy");
    }

    @Test
    void cardDownloadIsAuditedEvenWhenTheGatewayFails() {
        // A failed attempt to pull a national health ID is at least as
        // interesting to an investigation as a successful one.
        AbhaLinkageEntity linked = pendingLinkage();
        linked.setLinkageState(AbhaService.STATE_LINKED);
        linked.setAbhaNumber(ABHA_NUMBER);
        when(repository.findByPatientIdAndLinkageState(PATIENT, AbhaService.STATE_LINKED))
            .thenReturn(Optional.of(linked));
        when(abdm.fetchAbhaCard(ABHA_NUMBER)).thenThrow(new IllegalStateException("gateway down"));

        assertThrows(IllegalStateException.class,
            () -> service.downloadCard(PATIENT, null, null));

        verify(disclosureAudit).recordFailure(eq(PiiDisclosureAuditService.ABHA_CARD),
                                              eq(PATIENT), any(), any(), any(),
                                              eq("IllegalStateException"));
        verify(disclosureAudit, never()).recordSuccess(any(), any(), any(), any(), any());
    }

    @Test
    void cardDownloadRefusedWhenPatientHasNoLinkedAbha() {
        when(repository.findByPatientIdAndLinkageState(PATIENT, AbhaService.STATE_LINKED))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.downloadCard(PATIENT, null, null));
        verify(abdm, never()).fetchAbhaCard(anyString());
    }

    // ── demographic fallback (AB-005) ────────────────────────────────────────

    @Test
    void demographicVerificationLinksAndRecordsTheWeakerRoute() {
        AbhaLinkageEntity pending = pendingLinkage();
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(abdm.verifyByDemographics("txn-1", AADHAAR, "Ravi", "M", "1990"))
            .thenReturn(new AbdmClient.AbhaIdentity(ABHA_NUMBER, "ravi@abdm", "txn-1"));

        AbhaLinkageEntity row = service.verifyByDemographics(
            pending.getId(), AADHAAR, "Ravi", "M", "1990");

        assertEquals(AbhaService.STATE_LINKED, row.getLinkageState());
        assertEquals("tok:" + ABHA_NUMBER, row.getAbhaNumberToken());
        // An auditor must be able to tell OTP-verified linkages from these.
        assertTrue(row.getConsentVersion().endsWith(":DEMOGRAPHIC"));
    }

    @Test
    void demographicVerificationNeverPersistsAadhaar() {
        AbhaLinkageEntity pending = pendingLinkage();
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(abdm.verifyByDemographics(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new AbdmClient.AbhaIdentity(ABHA_NUMBER, "ravi@abdm", "txn-1"));

        AbhaLinkageEntity row = service.verifyByDemographics(
            pending.getId(), AADHAAR, "Ravi", "M", "1990");

        assertFalse(AADHAAR.equals(row.getAbhaNumber()));
        assertFalse(AADHAAR.equals(row.getAbhaNumberToken()));
        assertFalse(AADHAAR.equals(row.getTransactionId()));
    }

    private AbhaLinkageEntity pendingLinkage() {
        AbhaLinkageEntity e = new AbhaLinkageEntity();
        e.setId(UUID.randomUUID());
        e.setPatientId(PATIENT);
        e.setLinkageState(AbhaService.STATE_PENDING_OTP);
        e.setTransactionId("txn-1");
        return e;
    }
}
