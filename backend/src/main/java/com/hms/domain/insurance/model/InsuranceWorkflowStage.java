package com.hms.domain.insurance.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * The seven-stage manual TPA insurance desk workflow (WO-020).
 *
 * <p>This tracks the <b>fax-and-courier</b> claim process the insurance desk
 * runs for insurers that are not on NHCX. It is deliberately separate from
 * {@link InsuranceStatus}, which is the older flat lifecycle
 * (ACTIVE → PRE_AUTH_REQUESTED → SETTLED/REJECTED) still driving the existing
 * screens, and from the NHCX transaction states in
 * {@code com.hms.application.claims.PreAuthService}, which are gateway states
 * changed by asynchronous callbacks rather than by a human reading a fax.
 *
 * <p><b>Stored as a string</b>, not an ordinal. The legacy system this mirrors
 * stored ordinals, which is why its specification has to carry a "DO NOT
 * reorder" warning; declaration order here is a display and progression concern
 * only and is safe to change.
 *
 * <p><b>Progression is monotonic.</b> A desk clerk routinely goes back to
 * Stage 1 to correct a fax number after the docket has already been dispatched.
 * That edit must not drag the claim backwards to PREAUTHORISATION and make it
 * reappear on the "awaiting submission" worklist. So a stage submission updates
 * that stage's fields but only advances the current stage if the new stage
 * ranks higher — see {@link #advance(InsuranceWorkflowStage, InsuranceWorkflowStage)}.
 *
 * <p>A {@code null} current stage means "legacy record", created before this
 * workflow existed. V199 deliberately does not backfill one.
 */
public enum InsuranceWorkflowStage {

    /** Stage 1 — pre-auth request prepared and sent to the TPA. */
    PREAUTHORISATION(0, "Preauthorise"),

    /** Stage 2a — TPA sanctioned an amount. */
    PREAUTHORISATION_APPROVAL(1, "Preauthorise Approval"),

    /**
     * Stage 2b — TPA declined. Terminal for the claim; the admission converts
     * to cash. Kept at the same rank as approval so a rejection recorded after
     * an approval (TPA reversals happen) still registers as a stage change.
     */
    PREAUTHORISATION_REJECTED(1, "Preauthorise Rejected"),

    /** Stage 3 — mid-stay enhancement requested because charges exceeded the sanctioned limit. */
    ENHANCEMENT_REQUEST(2, "Enhancement Requested"),

    /** Stage 4a — TPA sanctioned a revised limit. */
    ENHANCEMENT_APPROVAL(3, "Enhancement Approval"),

    /** Stage 4b — TPA declined the enhancement; the claim still proceeds on the original limit. */
    ENHANCEMENT_REJECTED(3, "Enhancement Rejected"),

    /** Stage 5 — physical document checklist audited before the docket is packed. */
    CHECK_LIST_ENTRY(4, "Check-list Entry"),

    /** Stage 6 — docket couriered or emailed to the TPA. */
    DISPATCH_ENTRY(5, "Dispatched"),

    /** Stage 7 — settlement: cheques received, deductions itemised. */
    DISALLOWANCE_ENTRY(6, "Disallowance Entry");

    private final int rank;
    private final String label;

    InsuranceWorkflowStage(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    /**
     * Progression rank. Not the ordinal: two stages share a rank where they are
     * alternative outcomes of the same desk step (approved vs rejected).
     */
    public int rank() {
        return rank;
    }

    /** Human label for the timeline sidebar and report headings. */
    public String label() {
        return label;
    }

    /** Terminal stages — nothing follows them in the desk flow. */
    public boolean isTerminal() {
        return this == PREAUTHORISATION_REJECTED || this == DISALLOWANCE_ENTRY;
    }

    /**
     * The stages that may legitimately be worked next, for driving which steps
     * the UI unlocks.
     *
     * <p>Note this describes the <i>expected</i> path, not a hard gate. The only
     * transition rule enforced server-side is the bill-linkage prerequisite for
     * an enhancement request (see {@code InsuranceDeskService}); everything else
     * is advisory, because a desk that cannot record what actually happened
     * starts keeping a parallel spreadsheet.
     */
    public Set<InsuranceWorkflowStage> nextStages() {
        return switch (this) {
            case PREAUTHORISATION ->
                EnumSet.of(PREAUTHORISATION_APPROVAL, PREAUTHORISATION_REJECTED);
            case PREAUTHORISATION_APPROVAL ->
                EnumSet.of(ENHANCEMENT_REQUEST, CHECK_LIST_ENTRY);
            case PREAUTHORISATION_REJECTED ->
                EnumSet.noneOf(InsuranceWorkflowStage.class);
            case ENHANCEMENT_REQUEST ->
                EnumSet.of(ENHANCEMENT_APPROVAL, ENHANCEMENT_REJECTED);
            // A rejected enhancement still proceeds to dispatch — the claim is
            // filed for the originally sanctioned amount, and the hospital
            // pursues the balance from the patient.
            case ENHANCEMENT_APPROVAL, ENHANCEMENT_REJECTED ->
                EnumSet.of(CHECK_LIST_ENTRY);
            case CHECK_LIST_ENTRY ->
                EnumSet.of(DISPATCH_ENTRY);
            case DISPATCH_ENTRY ->
                EnumSet.of(DISALLOWANCE_ENTRY);
            case DISALLOWANCE_ENTRY ->
                EnumSet.noneOf(InsuranceWorkflowStage.class);
        };
    }

    /**
     * Monotonic advance. Returns whichever of the two stages ranks higher,
     * keeping {@code current} when the ranks tie.
     *
     * <p>Tie-keeping matters: re-saving Stage 2 as APPROVED on a claim already
     * marked PREAUTHORISATION_REJECTED (same rank) leaves the recorded stage
     * alone rather than silently flipping a rejection into an approval. A real
     * reversal is an explicit act, not a side effect of editing a fax number.
     *
     * @param current the stage recorded on the claim, may be {@code null} for a legacy row
     * @param submitted the stage just worked
     * @return the stage to persist, never {@code null}
     */
    public static InsuranceWorkflowStage advance(InsuranceWorkflowStage current,
                                                 InsuranceWorkflowStage submitted) {
        if (submitted == null) {
            throw new IllegalArgumentException("submitted stage is required");
        }
        if (current == null) {
            return submitted;
        }
        return submitted.rank() > current.rank() ? submitted : current;
    }

    /**
     * Whether {@code current} has reached or passed {@code stage} — the test the
     * timeline uses to decide which steps are unlocked and show a timestamp.
     */
    public static boolean hasReached(InsuranceWorkflowStage current, InsuranceWorkflowStage stage) {
        return current != null && stage != null && current.rank() >= stage.rank();
    }
}
