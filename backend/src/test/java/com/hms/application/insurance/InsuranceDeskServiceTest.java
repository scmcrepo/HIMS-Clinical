package com.hms.application.insurance;

import com.hms.api.insurance.request.*;
import com.hms.api.insurance.response.InsuranceDeskResponse;
import com.hms.application.billing.BillingOperationsService;
import com.hms.domain.insurance.model.*;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.insurance.InsuranceChequeReceiptJpaRepository;
import com.hms.infrastructure.persistence.insurance.InsuranceJpaRepository;
import com.hms.security.encryption.PiiSearchTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WO-020 / ID-003 — the desk service's business rules.
 *
 * <p>These cover the rules that cost money or lose evidence when they are wrong:
 * the bill-linkage gate on enhancements, the conditional communication and
 * decision fields, the POD requirement on a courier dispatch, and the routing of
 * itemised disallowances through the billing service rather than into a second
 * writer.
 *
 * <p>Tenant isolation is deliberately NOT asserted here — it is enforced by the
 * Hibernate filters on the entity, which a Mockito repository cannot exercise.
 * That belongs in a `@SpringBootTest` against a real datasource; see the task
 * card, which records it as outstanding rather than pretending a mock proved it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsuranceDeskServiceTest {

    @Mock private InsuranceJpaRepository insuranceRepo;
    @Mock private InsuranceChequeReceiptJpaRepository chequeRepo;
    @Mock private com.hms.infrastructure.persistence.patient.PatientJpaRepository patientRepo;
    @Mock private com.hms.infrastructure.sequence.NumberSequenceJpaRepository numberSequenceRepo;
    @Mock private com.hms.infrastructure.persistence.billing.BillJpaRepository billRepo;
    @Mock private BillingOperationsService billingService;
    @Mock private PiiSearchTokenService searchTokens;

    private final MeterRegistry meters = new SimpleMeterRegistry();
    private InsuranceDeskService service;

    private UUID insuranceId;
    private Insurance record;

    @BeforeEach
    void setUp() {
        service = new InsuranceDeskService(
            insuranceRepo, chequeRepo, patientRepo, numberSequenceRepo, billRepo, billingService, searchTokens, meters);

        insuranceId = UUID.randomUUID();
        record = new Insurance();
        record.setPatientId(UUID.randomUUID());
        record.setInsurerName("Star Health");

        when(insuranceRepo.findById(insuranceId)).thenReturn(Optional.of(record));
        when(insuranceRepo.save(any(Insurance.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chequeRepo.findByInsuranceIdOrderByChequeDateDesc(any())).thenReturn(List.of());
        when(searchTokens.token(anyString())).thenReturn("tok-abc");
    }

    // ── Stage 1 ─────────────────────────────────────────────────────────────

    @Test
    void preauthByFaxRequiresAFaxNumber() {
        var req = new PreauthStageRequest("CARD1", null, null, InsurancePreAuthType.PLANNED,
            ModeOfCommunication.FAX, "   ", null, null, 5_000_000L);

        var ex = assertThrows(BusinessRuleViolationException.class,
            () -> service.submitPreauth(insuranceId, req));
        assertTrue(ex.getMessage().contains("INSURANCE_FAX_REQUIRED"));
        verify(insuranceRepo, never()).save(any());
    }

    @Test
    void preauthByMailRequiresAMailId() {
        var req = new PreauthStageRequest("CARD1", null, null, null,
            ModeOfCommunication.MAIL, null, null, null, 5_000_000L);

        assertThrows(BusinessRuleViolationException.class,
            () -> service.submitPreauth(insuranceId, req));
    }

    @Test
    void preauthClearsTheEndpointThatDoesNotApply() {
        // Switching FAX to MAIL must not leave a stale fax number that a later
        // reader would take as the destination actually used.
        record.setPreauthFaxNo("044-12345678");

        var req = new PreauthStageRequest(null, null, null, null,
            ModeOfCommunication.MAIL, "044-12345678", "claims@tpa.example", null, 5_000_000L);
        service.submitPreauth(insuranceId, req);

        assertNull(record.getPreauthFaxNo());
        assertEquals("claims@tpa.example", record.getPreauthMailId());
    }

    @Test
    void preauthRecordsAnExpiredCardRatherThanRejectingIt() {
        // The desk must be able to capture what the patient actually presented.
        var req = new PreauthStageRequest("CARD1", LocalDate.now().minusDays(30), null, null,
            ModeOfCommunication.FAX, "044-12345678", null, null, 5_000_000L);

        InsuranceDeskResponse resp = service.submitPreauth(insuranceId, req);

        assertTrue(resp.cardExpired());
        assertEquals(InsuranceWorkflowStage.PREAUTHORISATION, record.getInsuranceCurrentStatus());
    }

    @Test
    void preauthStampsAppliedDateWhenNotSupplied() {
        var req = new PreauthStageRequest(null, null, null, null,
            ModeOfCommunication.FAX, "044-12345678", null, null, 5_000_000L);
        service.submitPreauth(insuranceId, req);

        assertNotNull(record.getPreauthAppliedDate());
        assertNotNull(record.getPreauthCreatedDate());
    }

    // ── Stage 2 ─────────────────────────────────────────────────────────────

    @Test
    void approvalRequiresASanctionedAmount() {
        var req = new PreauthApprovalStageRequest("CLM-1", TpaDecision.APPROVED,
            null, ModeOfCommunication.FAX, "044-1", null, null, null);

        var ex = assertThrows(BusinessRuleViolationException.class,
            () -> service.submitPreauthApproval(insuranceId, req));
        assertTrue(ex.getMessage().contains("INSURANCE_APPROVED_LIMIT_REQUIRED"));
    }

    @Test
    void rejectionRequiresAReason() {
        var req = new PreauthApprovalStageRequest("CLM-1", TpaDecision.REJECTED,
            null, ModeOfCommunication.FAX, "044-1", null, null, "  ");

        var ex = assertThrows(BusinessRuleViolationException.class,
            () -> service.submitPreauthApproval(insuranceId, req));
        assertTrue(ex.getMessage().contains("INSURANCE_REJECTION_REASON_REQUIRED"));
    }

    @Test
    void approvalStoresTheClaimNumberWithItsSearchToken() {
        // The claim number is encrypted, so an equality search can never match
        // it. Without the token the desk cannot find a claim by its TPA docket.
        var req = new PreauthApprovalStageRequest("CLM-4417", TpaDecision.APPROVED,
            null, ModeOfCommunication.FAX, "044-1", null, 10_000_000L, null);

        service.submitPreauthApproval(insuranceId, req);

        assertEquals("CLM-4417", record.getClaimNo());
        assertEquals("tok-abc", record.getClaimNoToken());
        verify(searchTokens).token("CLM-4417");
    }

    @Test
    void approvalAdvancesTheStageAndTheFlatLifecycleTogether() {
        var req = new PreauthApprovalStageRequest("CLM-1", TpaDecision.APPROVED,
            null, null, null, null, 10_000_000L, null);

        service.submitPreauthApproval(insuranceId, req);

        assertEquals(InsuranceWorkflowStage.PREAUTHORISATION_APPROVAL,
            record.getInsuranceCurrentStatus());
        // The older screens read the flat lifecycle; both models must agree.
        assertEquals(InsuranceStatus.PRE_AUTH_RECEIVED, record.getInsuranceStatus());
    }

    @Test
    void rejectionLandsOnTheRejectedStage() {
        var req = new PreauthApprovalStageRequest("CLM-1", TpaDecision.REJECTED,
            null, null, null, null, null, "Policy lapsed at admission");

        service.submitPreauthApproval(insuranceId, req);

        assertEquals(InsuranceWorkflowStage.PREAUTHORISATION_REJECTED,
            record.getInsuranceCurrentStatus());
        assertEquals(InsuranceStatus.REJECTED, record.getInsuranceStatus());
        assertEquals("Policy lapsed at admission", record.getPreauthRejectionReason());
    }

    // ── Stage 3: the bill-linkage gate ──────────────────────────────────────

    @Test
    void enhancementIsBlockedUntilABillIsLinked() {
        var req = new EnhancementStageRequest(null, null, 5_000_000L,
            ModeOfCommunication.FAX, "044-1", null, "Extended ICU stay");

        var ex = assertThrows(BusinessRuleViolationException.class,
            () -> service.submitEnhancement(insuranceId, req));

        assertTrue(ex.getMessage().contains("INSURANCE_BILL_NOT_LINKED"));
        // Nothing partial may be written — a half-recorded enhancement would
        // show on the worklist as sent when it never was.
        assertNull(record.getEnhancementRequestedAmount());
        assertNull(record.getReasonForEnhancement());
        verify(insuranceRepo, never()).save(any());
    }

    @Test
    void enhancementSucceedsOnceTheBillIsLinked() {
        record.setBillId(UUID.randomUUID());
        var req = new EnhancementStageRequest(InsurancePreAuthType.EMERGENCY, null, 5_000_000L,
            ModeOfCommunication.MAIL, null, "claims@tpa.example", "Extended ICU stay");

        service.submitEnhancement(insuranceId, req);

        assertEquals(5_000_000L, record.getEnhancementRequestedAmount());
        assertEquals("Extended ICU stay", record.getReasonForEnhancement());
        assertEquals(InsuranceWorkflowStage.ENHANCEMENT_REQUEST, record.getInsuranceCurrentStatus());
    }

    // ── Stage 4 ─────────────────────────────────────────────────────────────

    @Test
    void enhancementApprovalDoesNotOverwriteTheOriginalSanction() {
        record.setPreauthApprovalStatus(TpaDecision.APPROVED);
        record.setPreauthApprovedLimit(10_000_000L);

        var req = new EnhancementApprovalStageRequest(TpaDecision.APPROVED, null, null,
            15_000_000L, null);
        service.submitEnhancementApproval(insuranceId, req);

        assertEquals(10_000_000L, record.getPreauthApprovedLimit(),
            "the original sanction is what a short-payment dispute is argued against");
        assertEquals(15_000_000L, record.getEnhancementApprovedLimit());
        assertEquals(15_000_000L, record.effectiveApprovedLimit());
    }

    @Test
    void aRejectedEnhancementDoesNotRejectTheClaim() {
        record.setBillId(UUID.randomUUID());
        record.setPreauthApprovalStatus(TpaDecision.APPROVED);
        record.setPreauthApprovedLimit(10_000_000L);
        record.setInsuranceStatus(InsuranceStatus.PRE_AUTH_RECEIVED);

        service.submitEnhancementApproval(insuranceId,
            new EnhancementApprovalStageRequest(TpaDecision.REJECTED, null, null, null,
                "Sub-limit exhausted"));

        assertEquals(InsuranceWorkflowStage.ENHANCEMENT_REJECTED, record.getInsuranceCurrentStatus());
        // The claim still proceeds for the original sanction.
        assertNotEquals(InsuranceStatus.REJECTED, record.getInsuranceStatus());
        assertEquals(10_000_000L, record.effectiveApprovedLimit());
    }

    // ── Stage 5 ─────────────────────────────────────────────────────────────

    @Test
    void checklistIsStoredUnderTheChecklistsKey() {
        var req = new ChecklistStageRequest(List.of(
            new ChecklistStageRequest.ChecklistItem("Discharge Summary", 1, 1, null),
            new ChecklistStageRequest.ChecklistItem("Pharmacy Receipts", 5, 4, "1 lost by attender")));

        service.submitChecklist(insuranceId, req);

        Object items = record.getChecklist().get("checklists");
        assertInstanceOf(List.class, items);
        assertEquals(2, ((List<?>) items).size());
        assertEquals(InsuranceWorkflowStage.CHECK_LIST_ENTRY, record.getInsuranceCurrentStatus());
    }

    @Test
    void checklistReplacesRatherThanAppends() {
        service.submitChecklist(insuranceId, new ChecklistStageRequest(List.of(
            new ChecklistStageRequest.ChecklistItem("A", 1, 1, null),
            new ChecklistStageRequest.ChecklistItem("B", 1, 1, null))));

        service.submitChecklist(insuranceId, new ChecklistStageRequest(List.of(
            new ChecklistStageRequest.ChecklistItem("A", 1, 1, null))));

        assertEquals(1, ((List<?>) record.getChecklist().get("checklists")).size(),
            "a removed row must not resurrect on the next save");
    }

    // ── Stage 6 ─────────────────────────────────────────────────────────────

    @Test
    void courierDispatchRequiresAPodNumber() {
        var req = new DispatchStageRequest(ModeOfDispatch.COURIER, CourierVendor.DTDC,
            null, null, null, "Ravi", null);

        var ex = assertThrows(BusinessRuleViolationException.class,
            () -> service.submitDispatch(insuranceId, req));
        assertTrue(ex.getMessage().contains("INSURANCE_POD_REQUIRED"));
    }

    @Test
    void courierDispatchRequiresAVendor() {
        var req = new DispatchStageRequest(ModeOfDispatch.COURIER, null,
            "POD123", null, null, "Ravi", null);

        assertThrows(BusinessRuleViolationException.class,
            () -> service.submitDispatch(insuranceId, req));
    }

    @Test
    void emailDispatchRequiresADestination() {
        var req = new DispatchStageRequest(ModeOfDispatch.EMAIL, null,
            null, "  ", null, "Ravi", null);

        var ex = assertThrows(BusinessRuleViolationException.class,
            () -> service.submitDispatch(insuranceId, req));
        assertTrue(ex.getMessage().contains("INSURANCE_DISPATCH_MAIL_REQUIRED"));
    }

    @Test
    void emailDispatchDoesNotRetainCourierFields() {
        var req = new DispatchStageRequest(ModeOfDispatch.EMAIL, CourierVendor.DTDC,
            "POD123", "claims@tpa.example", null, "Ravi", null);

        service.submitDispatch(insuranceId, req);

        assertNull(record.getCourier());
        assertNull(record.getPodNo());
        assertEquals("claims@tpa.example", record.getDispatchMailId());
    }

    @Test
    void dispatchIncrementsItsMetric() {
        service.submitDispatch(insuranceId, new DispatchStageRequest(
            ModeOfDispatch.COURIER, CourierVendor.BLUE_DART, "POD-778", null, null, "Ravi", null));

        double count = meters.get("hms.insurance.desk.dispatch")
            .tag("mode", "COURIER").tag("courier", "BLUE_DART").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void stageTransitionMetricDistinguishesAdvancingFromUpdating() {
        service.submitPreauth(insuranceId, new PreauthStageRequest(null, null, null, null,
            ModeOfCommunication.FAX, "044-1", null, null, 1_000L));
        // Re-saving the same stage updates it without advancing.
        service.submitPreauth(insuranceId, new PreauthStageRequest(null, null, null, null,
            ModeOfCommunication.FAX, "044-2", null, null, 2_000L));

        assertEquals(1.0, meters.get("hms.insurance.desk.stage.transitions")
            .tag("stage", "PREAUTHORISATION").tag("outcome", "advanced").counter().count());
        assertEquals(1.0, meters.get("hms.insurance.desk.stage.transitions")
            .tag("stage", "PREAUTHORISATION").tag("outcome", "updated").counter().count());
    }

    // ── Stage 7 ─────────────────────────────────────────────────────────────

    @Test
    void disallowancesAreRoutedThroughTheBillingService() {
        // Not written here. BillingOperationsService owns
        // charge_line_items.disallowed_amount; a second writer is how the bill
        // and the claim start disagreeing about the same number.
        UUID lineA = UUID.randomUUID();
        UUID lineB = UUID.randomUUID();

        service.submitDisallowance(insuranceId, new DisallowanceStageRequest(
            List.of(),
            List.of(new DisallowanceStageRequest.DisallowanceLine(lineA, 250_000L),
                    new DisallowanceStageRequest.DisallowanceLine(lineB, 0L))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(billingService).updateDisallowedAmounts(captor.capture());

        List<Map<String, Object>> sent = captor.getValue();
        assertEquals(2, sent.size());
        assertEquals(lineA.toString(), sent.get(0).get("id"));
        assertEquals(250_000L, sent.get(0).get("disallowedAmount"));
        // Zero is meaningful — it clears a deduction keyed in error.
        assertEquals(0L, sent.get(1).get("disallowedAmount"));
    }

    @Test
    void chequesAreInsertedAndTheTotalIsReturned() {
        List<InsuranceChequeReceipt> saved = new ArrayList<>();
        when(chequeRepo.save(any())).thenAnswer(inv -> {
            InsuranceChequeReceipt r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            saved.add(r);
            return r;
        });
        when(chequeRepo.findByInsuranceIdOrderByChequeDateDesc(any()))
            .thenReturn(List.of())        // reconcile pass
            .thenReturn(saved);           // response pass

        var resp = service.submitDisallowance(insuranceId, new DisallowanceStageRequest(
            List.of(
                new DisallowanceStageRequest.ChequeReceiptItem(null, "CHQ-1",
                    LocalDate.now(), "HDFC", "Anna Nagar", 8_500_000L, "Claims Officer"),
                new DisallowanceStageRequest.ChequeReceiptItem(null, "CHQ-2",
                    LocalDate.now(), "HDFC", "Anna Nagar", 1_500_000L, "Claims Officer")),
            List.of()));

        assertEquals(2, saved.size());
        assertEquals(10_000_000L, resp.totalReceived());
        // Money received settles the flat lifecycle so the older screens stop
        // showing the claim as outstanding.
        assertEquals(InsuranceStatus.SETTLED, record.getInsuranceStatus());
    }

    @Test
    void chequesRemovedFromTheGridAreDeleted() {
        InsuranceChequeReceipt existing = new InsuranceChequeReceipt();
        existing.setId(UUID.randomUUID());
        existing.setInsuranceId(insuranceId);
        existing.setChequeNo("CHQ-OLD");
        existing.setAmount(500L);

        when(chequeRepo.findByInsuranceIdOrderByChequeDateDesc(any()))
            .thenReturn(List.of(existing));
        when(chequeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.submitDisallowance(insuranceId, new DisallowanceStageRequest(
            List.of(), List.of()));

        verify(chequeRepo).delete(existing);
    }

    @Test
    void aSettlementOnARejectedClaimDoesNotFlipItToSettled() {
        record.setInsuranceStatus(InsuranceStatus.REJECTED);
        when(chequeRepo.save(any())).thenAnswer(inv -> {
            InsuranceChequeReceipt r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        service.submitDisallowance(insuranceId, new DisallowanceStageRequest(
            List.of(new DisallowanceStageRequest.ChequeReceiptItem(null, "CHQ-1",
                LocalDate.now(), "HDFC", "Anna Nagar", 100L, null)),
            List.of()));

        assertEquals(InsuranceStatus.REJECTED, record.getInsuranceStatus());
    }

    // ── Bill linkage and search ─────────────────────────────────────────────

    @Test
    void linkBillActuallyPersistsTheBillId() {
        // Regression: the previous endpoint parsed both ids and returned the
        // record unchanged with a success message.
        UUID billId = UUID.randomUUID();

        service.linkBill(insuranceId, billId);

        assertEquals(billId, record.getBillId());
        verify(insuranceRepo).save(record);
    }

    @Test
    void linkBillRejectsANullBill() {
        assertThrows(BusinessRuleViolationException.class,
            () -> service.linkBill(insuranceId, null));
    }

    @Test
    void searchRejectsAnInvertedDateRange() {
        assertThrows(BusinessRuleViolationException.class,
            () -> service.searchByDateRange(
                LocalDate.now(), LocalDate.now().minusDays(1), null));
    }

    @Test
    void searchDefaultsToTheLastThirtyDays() {
        // Not "today": a desk screen defaulting to today shows an empty grid
        // every morning, which reads as a broken screen.
        when(insuranceRepo.findByCreatedAtBetween(any(), any())).thenReturn(List.of());

        service.searchByDateRange(null, null, null);

        ArgumentCaptor<java.time.Instant> start = ArgumentCaptor.forClass(java.time.Instant.class);
        ArgumentCaptor<java.time.Instant> end   = ArgumentCaptor.forClass(java.time.Instant.class);
        verify(insuranceRepo).findByCreatedAtBetween(start.capture(), end.capture());

        long days = java.time.Duration.between(start.getValue(), end.getValue()).toDays();
        assertEquals(31, days, "30 days inclusive of today");
    }

    @Test
    void searchFiltersByStageWhenOneIsGiven() {
        Insurance atDispatch = new Insurance();
        atDispatch.setInsuranceCurrentStatus(InsuranceWorkflowStage.DISPATCH_ENTRY);
        Insurance atPreauth = new Insurance();
        atPreauth.setInsuranceCurrentStatus(InsuranceWorkflowStage.PREAUTHORISATION);

        when(insuranceRepo.findByCreatedAtBetween(any(), any()))
            .thenReturn(List.of(atDispatch, atPreauth));

        var result = service.searchByDateRange(
            LocalDate.now().minusDays(7), LocalDate.now(), InsuranceWorkflowStage.DISPATCH_ENTRY);

        assertEquals(1, result.size());
        assertEquals(InsuranceWorkflowStage.DISPATCH_ENTRY, result.get(0).currentStage());
    }

    @Test
    void unknownInsuranceIdIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(insuranceRepo.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getDeskView(missing));
    }
}
