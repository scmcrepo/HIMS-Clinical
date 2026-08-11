package com.hms.application.claims;

import com.hms.application.claims.PreAuthEstimateCalculator.Line;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-015 / PA-002.
 *
 * <p>These numbers are quoted to a patient before admission, so the tests focus
 * on the two places the figure could be quietly wrong: the order in which
 * deductible and co-pay apply, and whether the total equals the lines shown
 * beside it.
 */
class PreAuthEstimateCalculatorTest {

    private static BigDecimal q(String s) {
        return new BigDecimal(s);
    }

    @Test
    void extendsLinesWithDecimalQuantities() {
        assertEquals(1_000_000L, PreAuthEstimateCalculator.lineAmount(q("2"), 500_000L));
        assertEquals(250_000L, PreAuthEstimateCalculator.lineAmount(q("0.5"), 500_000L));
        assertEquals(750_000L, PreAuthEstimateCalculator.lineAmount(q("1.5"), 500_000L));
    }

    @Test
    void totalAlwaysEqualsTheSumOfTheDisplayedLines() {
        // Summing unrounded lines and rounding once at the end gives a total
        // that does not match the visible lines — the first thing an insurer
        // queries.
        List<Line> lines = List.of(
            new Line("ROOM", q("3"), 500_000L),
            new Line("CONSUMABLE", q("2.5"), 33_333L),
            new Line("IMPLANT", q("1"), 4_000_000L));

        long summed = lines.stream()
            .mapToLong(l -> PreAuthEstimateCalculator.lineAmount(l.quantity(), l.unitAmountPaise()))
            .sum();

        assertEquals(summed, PreAuthEstimateCalculator.estimateTotal(lines));
    }

    @Test
    void categoryTotalIgnoresCase() {
        List<Line> lines = List.of(new Line("ROOM", q("3"), 500_000L));
        assertEquals(1_500_000L, PreAuthEstimateCalculator.categoryTotal(lines, "room"));
    }

    @Test
    void emptyEstimateIsZeroNotAnError() {
        assertEquals(0L, PreAuthEstimateCalculator.estimateTotal(List.of()));
    }

    @Test
    void roomShortfallIsChargeMinusCapTimesStay() {
        assertEquals(300_000L,
            PreAuthEstimateCalculator.roomShortfall(1_500_000L, 400_000L, 3));
        assertEquals(0L, PreAuthEstimateCalculator.roomShortfall(1_500_000L, 500_000L, 3));
    }

    @Test
    void noStatedRoomCapMeansNoShortfall() {
        // Treating a null cap as zero would show the patient the entire room
        // charge as an out-of-pocket shortfall.
        assertEquals(0L, PreAuthEstimateCalculator.roomShortfall(1_500_000L, null, 3));
    }

    @Test
    void deductibleAppliesBeforeCoPayNotAfter() {
        // ₹1,00,000 estimate, ₹10,000 deductible, 10% co-pay.
        // Correct: 10,000 + 10% of 90,000 = 19,000.
        // Wrong:   10,000 + 10% of 1,00,000 = 20,000 — overstates the quote.
        assertEquals(1_900_000L,
            PreAuthEstimateCalculator.patientLiability(10_000_000L, 1000, 1_000_000L, 0L));
    }

    @Test
    void deductibleLargerThanTheEstimateClampsToTheEstimate() {
        assertEquals(10_000_000L,
            PreAuthEstimateCalculator.patientLiability(10_000_000L, 1000, 50_000_000L, 0L));
    }

    @Test
    void roomShortfallAddsOnTopOfTheCoPay() {
        assertEquals(2_200_000L,
            PreAuthEstimateCalculator.patientLiability(10_000_000L, 1000, 1_000_000L, 300_000L));
    }

    @Test
    void noCoPayAndNoDeductibleMeansNothingOutOfPocket() {
        assertEquals(0L,
            PreAuthEstimateCalculator.patientLiability(10_000_000L, null, null, 0L));
    }

    @Test
    void fractionalCoPayIsHandled() {
        assertEquals(750_000L,
            PreAuthEstimateCalculator.patientLiability(10_000_000L, 750, null, 0L));
    }

    @Test
    void unknownBalanceDoesNotBlockAdmission() {
        // An unverified policy is not evidence of insufficient cover, and
        // blocking on missing data punishes the patient for a payer outage.
        assertFalse(PreAuthEstimateCalculator.exceedsAvailableBalance(10_000_000L, null));
        assertTrue(PreAuthEstimateCalculator.exceedsAvailableBalance(10_000_000L, 5_000_000L));
        assertFalse(PreAuthEstimateCalculator.exceedsAvailableBalance(10_000_000L, 10_000_000L));
    }

    @Test
    void enhancementAsksForTheDeltaNotTheRevisedTotal() {
        assertEquals(2_000_000L,
            PreAuthEstimateCalculator.enhancementDelta(8_000_000L, 10_000_000L));
    }

    @Test
    void enhancementBelowWhatIsApprovedIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> PreAuthEstimateCalculator.enhancementDelta(10_000_000L, 8_000_000L));
        assertThrows(IllegalArgumentException.class,
            () -> PreAuthEstimateCalculator.enhancementDelta(10_000_000L, 10_000_000L));
    }

    @Test
    void shortApprovalIsDetectedAndNoAnswerIsNotApproval() {
        assertFalse(PreAuthEstimateCalculator.isFullyApproved(10_000_000L, 8_000_000L));
        assertTrue(PreAuthEstimateCalculator.isFullyApproved(10_000_000L, 10_000_000L));
        assertFalse(PreAuthEstimateCalculator.isFullyApproved(10_000_000L, null));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> PreAuthEstimateCalculator.lineAmount(q("0"), 100L));
        assertThrows(IllegalArgumentException.class,
            () -> PreAuthEstimateCalculator.lineAmount(q("-1"), 100L));
        assertThrows(IllegalArgumentException.class,
            () -> PreAuthEstimateCalculator.patientLiability(100L, 20_000, null, 0L));
        assertThrows(IllegalArgumentException.class,
            () -> PreAuthEstimateCalculator.roomShortfall(100L, 10L, -1));
    }
}
