package com.hms.application.claims;

import com.hms.application.claims.ClaimSettlementCalculator.Split;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-016 / CP-002.
 *
 * <p>Money rules, so the properties matter more than the examples. The
 * co-pay split is property-tested rather than spot-checked: an example test
 * proves one case, and the failure mode here is a rounding drift that only
 * appears on particular amounts.
 */
class ClaimSettlementCalculatorTest {

    @Test
    void disallowedIsClaimedMinusApproved() {
        assertEquals(500_000L, ClaimSettlementCalculator.disallowed(10_000_000L, 9_500_000L));
        assertEquals(0L, ClaimSettlementCalculator.disallowed(10_000_000L, 10_000_000L));
    }

    @Test
    void overApprovalClampsRatherThanGoingNegative() {
        // A negative disallowance would read on the control tower as money the
        // hospital owes back, which is not what an over-approval means.
        assertEquals(0L, ClaimSettlementCalculator.disallowed(9_500_000L, 10_000_000L));
    }

    @Test
    void coPaySplitsAtTenPercent() {
        Split s = ClaimSettlementCalculator.coPaySplit(10_000_000L, 1000);
        assertEquals(1_000_000L, s.patientPaise());
        assertEquals(9_000_000L, s.insurerPaise());
    }

    @Test
    void coPaySharesAlwaysSumBackToTheApprovedAmount() {
        Random r = new Random(42);
        int[] basisPoints = {0, 1, 750, 1000, 3333, 9999, 10_000};
        for (int i = 0; i < 5_000; i++) {
            long amount = Math.abs(r.nextLong() % 10_000_000_000L);
            for (int bp : basisPoints) {
                Split s = ClaimSettlementCalculator.coPaySplit(amount, bp);
                assertEquals(amount, s.total(),
                    "shares must sum exactly for amount=" + amount + " bp=" + bp);
                assertTrue(s.patientPaise() >= 0 && s.insurerPaise() >= 0);
            }
        }
    }

    @Test
    void zeroCoPayGivesEverythingToTheInsurer() {
        Split s = ClaimSettlementCalculator.coPaySplit(500_000L, 0);
        assertEquals(0L, s.patientPaise());
        assertEquals(500_000L, s.insurerPaise());
    }

    @Test
    void netPayableSubtractsTdsAndDeductions() {
        assertEquals(8_500_000L,
            ClaimSettlementCalculator.netPayable(9_500_000L, 500_000L, 500_000L));
    }

    @Test
    void netPayableClampsAtZero() {
        // A bank cannot transfer a negative amount.
        assertEquals(0L, ClaimSettlementCalculator.netPayable(100_000L, 200_000L, 0L));
    }

    @Test
    void reconciliationIsExactWithNoTolerance() {
        assertTrue(ClaimSettlementCalculator.reconciles(8_500_000L, 8_500_000L));
        // One rupee short must surface for a human, not be absorbed.
        assertFalse(ClaimSettlementCalculator.reconciles(8_500_000L, 8_499_900L));
    }

    @Test
    void reconciliationGapIsSignedSoOverCreditIsVisible() {
        assertEquals(100L, ClaimSettlementCalculator.reconciliationGap(8_500_000L, 8_499_900L));
        assertEquals(-100L, ClaimSettlementCalculator.reconciliationGap(8_499_900L, 8_500_000L));
    }

    @Test
    void financialStateFollowsTheLifecycle() {
        assertEquals("CLAIM_SUBMITTED",
            ClaimSettlementCalculator.financialState(false, false, false, false));
        assertEquals("CLAIM_APPROVED",
            ClaimSettlementCalculator.financialState(true, false, false, false));
        assertEquals("PAYMENT_INITIATED",
            ClaimSettlementCalculator.financialState(true, true, false, false));
        assertEquals("AMOUNT_RECEIVED_IN_BANK",
            ClaimSettlementCalculator.financialState(true, true, true, false));
    }

    @Test
    void disputeOverridesASettledClaim() {
        // Money arriving for part of a claim must not hide the dispute.
        assertEquals("CLAIM_DISPUTED",
            ClaimSettlementCalculator.financialState(true, true, true, true));
    }

    @Test
    void tdsAloneDoesNotWarrantADispute() {
        // Statutory withholding is reclaimed against tax, not challenged with
        // the payer. Raising it trains billing to ignore the flag.
        assertFalse(ClaimSettlementCalculator.warrantsDispute(0L, 500_000L));
        assertTrue(ClaimSettlementCalculator.warrantsDispute(500_000L, 0L));
    }

    @Test
    void negativeAmountsAndImpossibleCoPaysAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> ClaimSettlementCalculator.disallowed(-1L, 0L));
        assertThrows(IllegalArgumentException.class,
            () -> ClaimSettlementCalculator.netPayable(100L, -1L, 0L));
        assertThrows(IllegalArgumentException.class,
            () -> ClaimSettlementCalculator.coPaySplit(100L, 20_000));
        assertThrows(IllegalArgumentException.class,
            () -> ClaimSettlementCalculator.coPaySplit(100L, -1));
    }
}
