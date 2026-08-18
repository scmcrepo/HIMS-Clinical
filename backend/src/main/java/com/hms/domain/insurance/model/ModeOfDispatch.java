package com.hms.domain.insurance.model;

/**
 * How the completed claim docket left the hospital (WO-020, Stage 6).
 *
 * <p>{@link #COURIER} requires a courier vendor and carries a POD number;
 * {@link #EMAIL} requires a destination mail id. The distinction is what makes
 * "prove you sent it" answerable months later when a TPA denies receipt.
 */
public enum ModeOfDispatch {
    COURIER,
    EMAIL
}
