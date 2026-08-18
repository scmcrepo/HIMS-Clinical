package com.hms.domain.insurance;

import com.hms.domain.insurance.model.Insurance;
import com.hms.domain.insurance.model.InsuranceWorkflowStage;
import com.hms.domain.insurance.model.TpaDecision;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static com.hms.domain.insurance.model.InsuranceWorkflowStage.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WO-020 / ID-002 — the desk state machine.
 *
 * <p>Pure domain logic with no Spring context, so these run fast and are the
 * first thing to check when a claim ends up on the wrong worklist. The
 * monotonicity property is the one that matters: a desk clerk correcting a fax
 * number on a dispatched claim must not send it back to the top of the queue.
 */
class InsuranceWorkflowStageTest {

    // ── Monotonic advance ───────────────────────────────────────────────────

    @Test
    void advanceFromNullTakesTheSubmittedStage() {
        // A legacy record — created before the desk flow existed — adopts
        // whatever stage is first worked on it.
        assertEquals(PREAUTHORISATION, InsuranceWorkflowStage.advance(null, PREAUTHORISATION));
        assertEquals(DISPATCH_ENTRY, InsuranceWorkflowStage.advance(null, DISPATCH_ENTRY));
    }

    @Test
    void advanceMovesForwardWhenTheSubmittedStageRanksHigher() {
        assertEquals(PREAUTHORISATION_APPROVAL,
            InsuranceWorkflowStage.advance(PREAUTHORISATION, PREAUTHORISATION_APPROVAL));
        assertEquals(DISALLOWANCE_ENTRY,
            InsuranceWorkflowStage.advance(DISPATCH_ENTRY, DISALLOWANCE_ENTRY));
    }

    @Test
    void advanceNeverMovesBackwards() {
        // The whole point. Correcting Stage 1 after the docket shipped keeps the
        // claim at DISPATCH_ENTRY rather than resurrecting it as unsubmitted.
        assertEquals(DISPATCH_ENTRY,
            InsuranceWorkflowStage.advance(DISPATCH_ENTRY, PREAUTHORISATION));
        assertEquals(DISALLOWANCE_ENTRY,
            InsuranceWorkflowStage.advance(DISALLOWANCE_ENTRY, ENHANCEMENT_REQUEST));
    }

    @Test
    void advanceKeepsTheCurrentStageOnATie() {
        // Approval and rejection share a rank. Re-saving Stage 2 must not flip a
        // recorded rejection into an approval as a side effect — a reversal is
        // an explicit act.
        assertEquals(PREAUTHORISATION_REJECTED,
            InsuranceWorkflowStage.advance(PREAUTHORISATION_REJECTED, PREAUTHORISATION_APPROVAL));
        assertEquals(ENHANCEMENT_APPROVAL,
            InsuranceWorkflowStage.advance(ENHANCEMENT_APPROVAL, ENHANCEMENT_REJECTED));
    }

    @Test
    void advanceRejectsANullSubmission() {
        assertThrows(IllegalArgumentException.class,
            () -> InsuranceWorkflowStage.advance(PREAUTHORISATION, null));
    }

    @Test
    void advanceIsIdempotent() {
        for (InsuranceWorkflowStage s : values()) {
            assertEquals(s, InsuranceWorkflowStage.advance(s, s),
                "resubmitting the same stage must not change it: " + s);
        }
    }

    // ── Ranking and reachability ────────────────────────────────────────────

    @Test
    void ranksIncreaseAlongTheHappyPath() {
        assertTrue(PREAUTHORISATION.rank() < PREAUTHORISATION_APPROVAL.rank());
        assertTrue(PREAUTHORISATION_APPROVAL.rank() < ENHANCEMENT_REQUEST.rank());
        assertTrue(ENHANCEMENT_REQUEST.rank() < ENHANCEMENT_APPROVAL.rank());
        assertTrue(ENHANCEMENT_APPROVAL.rank() < CHECK_LIST_ENTRY.rank());
        assertTrue(CHECK_LIST_ENTRY.rank() < DISPATCH_ENTRY.rank());
        assertTrue(DISPATCH_ENTRY.rank() < DISALLOWANCE_ENTRY.rank());
    }

    @Test
    void alternativeOutcomesShareARank() {
        assertEquals(PREAUTHORISATION_APPROVAL.rank(), PREAUTHORISATION_REJECTED.rank());
        assertEquals(ENHANCEMENT_APPROVAL.rank(), ENHANCEMENT_REJECTED.rank());
    }

    @Test
    void hasReachedIsFalseForALegacyRecord() {
        // Null is "no desk stage recorded", not "stage zero". A legacy record
        // must not render as having completed Stage 1.
        assertFalse(InsuranceWorkflowStage.hasReached(null, PREAUTHORISATION));
    }

    @Test
    void hasReachedIsTrueForTheCurrentStageAndEverythingBefore() {
        assertTrue(InsuranceWorkflowStage.hasReached(DISPATCH_ENTRY, PREAUTHORISATION));
        assertTrue(InsuranceWorkflowStage.hasReached(DISPATCH_ENTRY, DISPATCH_ENTRY));
        assertFalse(InsuranceWorkflowStage.hasReached(DISPATCH_ENTRY, DISALLOWANCE_ENTRY));
    }

    // ── Transition table ────────────────────────────────────────────────────

    @Test
    void rejectedPreauthIsTerminal() {
        assertTrue(PREAUTHORISATION_REJECTED.isTerminal());
        assertTrue(PREAUTHORISATION_REJECTED.nextStages().isEmpty());
    }

    @Test
    void aRejectedEnhancementStillProceedsToDispatch() {
        // The claim is filed for the originally sanctioned amount; the hospital
        // pursues the balance from the patient. Treating this as terminal would
        // strand the claim undispatched.
        assertFalse(ENHANCEMENT_REJECTED.isTerminal());
        assertTrue(ENHANCEMENT_REJECTED.nextStages().contains(CHECK_LIST_ENTRY));
    }

    @Test
    void enhancementMayBeSkippedEntirely() {
        // Most claims never need one — approval goes straight to the checklist.
        assertTrue(PREAUTHORISATION_APPROVAL.nextStages().contains(CHECK_LIST_ENTRY));
        assertTrue(PREAUTHORISATION_APPROVAL.nextStages().contains(ENHANCEMENT_REQUEST));
    }

    @Test
    void everyStageHasALabel() {
        for (InsuranceWorkflowStage s : values()) {
            assertNotNull(s.label());
            assertFalse(s.label().isBlank(), "missing label for " + s);
        }
    }

    // ── Entity behaviour that the desk depends on ───────────────────────────

    @Test
    void effectiveLimitPrefersAnApprovedEnhancement() {
        Insurance ins = new Insurance();
        ins.setPreauthApprovalStatus(TpaDecision.APPROVED);
        ins.setPreauthApprovedLimit(10_000_000L);          // ₹1,00,000
        ins.setEnhancementApprovalStatus(TpaDecision.APPROVED);
        ins.setEnhancementApprovedLimit(15_000_000L);      // ₹1,50,000

        assertEquals(15_000_000L, ins.effectiveApprovedLimit());
        // The original sanction survives, which is what a short-payment dispute
        // is argued against.
        assertEquals(10_000_000L, ins.getPreauthApprovedLimit());
    }

    @Test
    void effectiveLimitFallsBackToPreauthWhenTheEnhancementWasRejected() {
        Insurance ins = new Insurance();
        ins.setPreauthApprovalStatus(TpaDecision.APPROVED);
        ins.setPreauthApprovedLimit(10_000_000L);
        ins.setEnhancementApprovalStatus(TpaDecision.REJECTED);
        ins.setEnhancementApprovedLimit(null);

        assertEquals(10_000_000L, ins.effectiveApprovedLimit());
    }

    @Test
    void effectiveLimitIsNullWhenNothingIsSanctionedYet() {
        // Null, not zero. Zero reads as "the TPA sanctioned nothing", which is a
        // different and much worse fact than "the TPA has not replied".
        assertNull(new Insurance().effectiveApprovedLimit());
    }

    @Test
    void cardExpiryComparesStrictlyBeforeToday() {
        Insurance ins = new Insurance();
        LocalDate today = LocalDate.of(2026, 8, 15);

        ins.setCardValidity(today);
        assertFalse(ins.isCardExpired(today), "a card valid through today is not expired");

        ins.setCardValidity(today.minusDays(1));
        assertTrue(ins.isCardExpired(today));

        ins.setCardValidity(today.plusDays(1));
        assertFalse(ins.isCardExpired(today));
    }

    @Test
    void aCardWithNoRecordedExpiryIsNotTreatedAsExpired() {
        // Most walk-in cards have no expiry keyed. Defaulting to "expired" would
        // put an amber warning on every claim and train the desk to ignore it.
        assertFalse(new Insurance().isCardExpired(LocalDate.now()));
    }

    @Test
    void billLinkageGatesTheEnhancementStage() {
        Insurance ins = new Insurance();
        assertFalse(ins.isBillLinked());
        ins.setBillId(UUID.randomUUID());
        assertTrue(ins.isBillLinked());
    }

    @Test
    void advanceStageOnTheEntityIsMonotonic() {
        Insurance ins = new Insurance();
        ins.advanceStage(PREAUTHORISATION);
        ins.advanceStage(DISPATCH_ENTRY);
        ins.advanceStage(PREAUTHORISATION);
        assertEquals(DISPATCH_ENTRY, ins.getInsuranceCurrentStatus());
    }
}
