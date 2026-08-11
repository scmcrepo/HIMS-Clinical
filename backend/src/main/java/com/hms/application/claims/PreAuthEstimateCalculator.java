package com.hms.application.claims;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Estimate arithmetic for cashless pre-authorisation — Module 4.
 *
 * <p>Dependency-free for the same reason as {@link ClaimSettlementCalculator}:
 * these numbers decide what the hospital tells a patient they will owe before
 * admission, and a class with nothing to bootstrap can be tested anywhere.
 *
 * <p><b>All amounts in paise.</b> Quantity is the one place a decimal is
 * legitimate — half a day of room rent, 1.5 units of an implant — so it is
 * carried as {@link BigDecimal} and multiplied out with explicit rounding
 * rather than as a double.
 */
public final class PreAuthEstimateCalculator {

    private PreAuthEstimateCalculator() {
    }

    /** One line of the estimate. */
    public record Line(String category, BigDecimal quantity, long unitAmountPaise) {
    }

    /**
     * Extend a line: quantity times unit price, rounded half-up to the paise.
     *
     * <p>Rounding happens once, at the line, and the total is the sum of rounded
     * lines. Summing unrounded lines and rounding at the end produces a total
     * that does not equal the visible lines, which is the first thing an insurer
     * queries.
     */
    public static long lineAmount(BigDecimal quantity, long unitAmountPaise) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (unitAmountPaise < 0) {
            throw new IllegalArgumentException("unit amount cannot be negative");
        }
        return quantity.multiply(BigDecimal.valueOf(unitAmountPaise))
                       .setScale(0, RoundingMode.HALF_UP)
                       .longValueExact();
    }

    /** Estimate total. Sum of extended lines, so it always matches what is shown. */
    public static long estimateTotal(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Line l : lines) {
            total += lineAmount(l.quantity(), l.unitAmountPaise());
        }
        return total;
    }

    /** Sum of one category, for the room-cap check. */
    public static long categoryTotal(List<Line> lines, String category) {
        if (lines == null || category == null) {
            return 0L;
        }
        long total = 0L;
        for (Line l : lines) {
            if (category.equalsIgnoreCase(l.category())) {
                total += lineAmount(l.quantity(), l.unitAmountPaise());
            }
        }
        return total;
    }

    /**
     * Room charge the policy will not cover, given a daily cap and stay length.
     *
     * <p>Returns zero when no cap was stated. A payer that did not state a cap
     * has not stated a cap of nothing, and treating {@code null} as zero here
     * would show the patient the entire room charge as a shortfall.
     *
     * @param roomCapPerDayPaise null when the payer stated no limit
     */
    public static long roomShortfall(long roomChargePaise, Long roomCapPerDayPaise,
                                     int expectedLosDays) {
        if (roomCapPerDayPaise == null) {
            return 0L;
        }
        if (expectedLosDays < 0) {
            throw new IllegalArgumentException("length of stay cannot be negative");
        }
        long covered = roomCapPerDayPaise * (long) expectedLosDays;
        return Math.max(0L, roomChargePaise - covered);
    }

    /**
     * What the patient is likely to pay out of pocket.
     *
     * <p>Deductible first, then co-pay on the remainder, then any room
     * shortfall. That order matters: applying co-pay to the pre-deductible
     * figure overstates the patient's share, and this number is what the desk
     * quotes at admission.
     *
     * @param basisPoints 10% is 1000
     */
    public static long patientLiability(long estimateTotalPaise, Integer basisPoints,
                                        Long deductiblePaise, long roomShortfallPaise) {
        if (estimateTotalPaise < 0) {
            throw new IllegalArgumentException("estimate cannot be negative");
        }
        long deductible = deductiblePaise == null ? 0L : deductiblePaise;
        if (deductible < 0) {
            throw new IllegalArgumentException("deductible cannot be negative");
        }

        long afterDeductible = Math.max(0L, estimateTotalPaise - deductible);
        long appliedDeductible = estimateTotalPaise - afterDeductible;

        long coPay = 0L;
        if (basisPoints != null && basisPoints > 0) {
            if (basisPoints > 10_000) {
                throw new IllegalArgumentException("co-pay basis points must be 0..10000");
            }
            coPay = BigDecimal.valueOf(afterDeductible)
                              .multiply(BigDecimal.valueOf(basisPoints))
                              .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                              .longValueExact();
        }

        return appliedDeductible + coPay + roomShortfallPaise;
    }

    /**
     * Whether the estimate exceeds the balance the policy has left.
     *
     * <p>Unknown balance returns false rather than true. An unverified policy is
     * not evidence of insufficient cover, and blocking admission on missing data
     * would punish the patient for a payer outage.
     */
    public static boolean exceedsAvailableBalance(long estimateTotalPaise, Long balancePaise) {
        return balancePaise != null && estimateTotalPaise > balancePaise;
    }

    /**
     * The shortfall an enhancement should ask for.
     *
     * <p>The delta, not the revised total. Asking for the full revised figure
     * when part is already approved is how a hospital ends up double-counting
     * an approval.
     */
    public static long enhancementDelta(long previousApprovedPaise, long revisedEstimatePaise) {
        if (previousApprovedPaise < 0 || revisedEstimatePaise < 0) {
            throw new IllegalArgumentException("amounts cannot be negative");
        }
        if (revisedEstimatePaise <= previousApprovedPaise) {
            throw new IllegalArgumentException(
                "A revised estimate must exceed what is already approved");
        }
        return revisedEstimatePaise - previousApprovedPaise;
    }

    /**
     * Whether an insurer's approval covers the estimate in full.
     *
     * <p>A short approval is the trigger for either a query response or an
     * enhancement, so it is worth naming rather than leaving each screen to
     * compare two numbers its own way.
     */
    public static boolean isFullyApproved(long estimateTotalPaise, Long approvedPaise) {
        return approvedPaise != null && approvedPaise >= estimateTotalPaise;
    }
}
