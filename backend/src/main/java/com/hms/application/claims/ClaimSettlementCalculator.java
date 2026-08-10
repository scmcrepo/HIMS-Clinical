package com.hms.application.claims;

/**
 * The arithmetic behind claim settlement and bank reconciliation — Module 5.
 *
 * <p>Deliberately a plain class with no Spring, no logging and no persistence
 * dependencies. Everything here is a pure function of longs. That is partly
 * design discipline and partly practical: money rules are the code most worth
 * testing in isolation, and a class with no container to start can be executed
 * anywhere, including in environments where the full application cannot be
 * built.
 *
 * <p><b>All amounts are in paise.</b> Every method takes and returns paise as
 * {@code long}. No method accepts a {@code double}, and that is not an
 * oversight — a rupee amount in binary floating point cannot represent 0.07
 * exactly, and the error compounds across a hospital's monthly claim volume into
 * a reconciliation nobody can close.
 */
public final class ClaimSettlementCalculator {

    private ClaimSettlementCalculator() {
    }

    /** The five financial states from the flow document, in lifecycle order. */
    public static final String CLAIM_SUBMITTED = "CLAIM_SUBMITTED";
    public static final String CLAIM_APPROVED = "CLAIM_APPROVED";
    public static final String PAYMENT_INITIATED = "PAYMENT_INITIATED";
    public static final String AMOUNT_RECEIVED_IN_BANK = "AMOUNT_RECEIVED_IN_BANK";
    public static final String CLAIM_DISPUTED = "CLAIM_DISPUTED";

    /**
     * What the insurer disallowed.
     *
     * <p>Derived from claimed minus approved rather than trusted from a payer
     * field, so the number the billing team disputes is always consistent with
     * the two figures beside it on screen.
     */
    public static long disallowed(long claimedPaise, long approvedPaise) {
        requireNonNegative(claimedPaise, "claimed");
        requireNonNegative(approvedPaise, "approved");
        // An approval above the claim is a payer data error. Treat the excess as
        // nothing disallowed rather than a negative disallowance, which would
        // read on the control tower as money owed back to the insurer.
        return Math.max(0L, claimedPaise - approvedPaise);
    }

    /**
     * Split an approved amount between insurer and patient at a co-pay rate.
     *
     * <p>The patient's share is computed and the insurer takes the remainder.
     * Computing both independently and rounding each lets the two fail to sum
     * to the bill — a rupee adrift on every claim, which is invisible per claim
     * and unreconcilable per month.
     *
     * @param basisPoints 10% is 1000; 7.5% is 750
     */
    public static Split coPaySplit(long approvedPaise, int basisPoints) {
        requireNonNegative(approvedPaise, "approved");
        if (basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("co-pay basis points must be 0..10000");
        }
        if (basisPoints == 0) {
            return new Split(0L, approvedPaise);
        }
        // Integer arithmetic with explicit half-up rounding. Multiplying first
        // keeps full precision; approvedPaise would have to exceed ~9.2e14
        // paise (₹9.2 trillion) to overflow a long here.
        long patient = Math.round((double) approvedPaise * basisPoints / 10_000.0);
        // Guard the rounding rather than trusting the double: recompute the
        // remainder so the two shares are exact complements by construction.
        return new Split(patient, approvedPaise - patient);
    }

    /**
     * What the insurer should transfer after TDS and deductions.
     *
     * <p>Clamped at zero: TDS plus deductions exceeding the approved amount is a
     * payer data error, and a negative disbursal is not a thing a bank can do.
     */
    public static long netPayable(long approvedPaise, long tdsPaise, long deductionPaise) {
        requireNonNegative(approvedPaise, "approved");
        requireNonNegative(tdsPaise, "tds");
        requireNonNegative(deductionPaise, "deduction");
        return Math.max(0L, approvedPaise - tdsPaise - deductionPaise);
    }

    /**
     * Does what the bank credited match what the insurer said it sent?
     *
     * <p>Exact match only. A tolerance here would be a decision to silently
     * absorb small shortfalls, and across thousands of claims "small" becomes
     * material. A mismatch is surfaced for a human, not smoothed over.
     */
    public static boolean reconciles(long advisedNetPaise, long bankCreditedPaise) {
        return advisedNetPaise == bankCreditedPaise;
    }

    /** Signed gap between advice and bank credit. Positive means short-paid. */
    public static long reconciliationGap(long advisedNetPaise, long bankCreditedPaise) {
        return advisedNetPaise - bankCreditedPaise;
    }

    /**
     * The financial state a claim should be in.
     *
     * <p>Centralised so the control tower, the callback handler and the
     * reconciliation screen cannot disagree about what a claim's status is.
     */
    public static String financialState(boolean approved, boolean paymentAdvised,
                                        boolean bankReconciled, boolean disputed) {
        // Dispute wins: a disputed claim must not display as settled merely
        // because money arrived for part of it.
        if (disputed) return CLAIM_DISPUTED;
        if (bankReconciled) return AMOUNT_RECEIVED_IN_BANK;
        if (paymentAdvised) return PAYMENT_INITIATED;
        if (approved) return CLAIM_APPROVED;
        return CLAIM_SUBMITTED;
    }

    /**
     * Whether a shortfall warrants raising a dispute.
     *
     * <p>Only a disallowance does. TDS is statutory withholding the hospital
     * reclaims against its own tax liability, not money the payer wrongly kept,
     * so a claim reduced purely by TDS must not raise a dispute — doing so
     * floods the payer with invalid challenges and trains the billing team to
     * ignore the flag.
     */
    public static boolean warrantsDispute(long disallowedPaise, long tdsPaise) {
        return disallowedPaise > 0;
    }

    /** Insurer share, patient share. Always sums exactly to the input. */
    public record Split(long patientPaise, long insurerPaise) {
        public long total() {
            return patientPaise + insurerPaise;
        }
    }

    private static void requireNonNegative(long value, String what) {
        if (value < 0) {
            throw new IllegalArgumentException(what + " amount cannot be negative");
        }
    }
}
