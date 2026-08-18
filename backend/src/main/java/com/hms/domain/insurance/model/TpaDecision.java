package com.hms.domain.insurance.model;

/**
 * The TPA's answer to a pre-auth or enhancement request (WO-020, Stages 2 & 4).
 *
 * <p>Deliberately only two values. "In process" is not a decision the TPA sent —
 * it is the absence of one, represented by a null decision on the claim, and
 * modelling it as a third enum value would make "no answer yet" and "answered
 * with a shrug" indistinguishable in the status reports.
 */
public enum TpaDecision {
    APPROVED,
    REJECTED
}
