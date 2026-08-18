package com.hms.api.insurance.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Stage 5 — the pre-dispatch document checklist (WO-020).
 *
 * <p>Submitted as a whole list each time; the service replaces the stored
 * manifest rather than patching rows. A checklist is small, edited as a unit at
 * a desk, and last-write-wins is the behaviour the clerk expects.
 */
public record ChecklistStageRequest(
    @Valid @NotNull List<ChecklistItem> checklists
) {

    /**
     * One line of the manifest.
     *
     * <p>Counts, not booleans: "5 pharmacy receipts expected, 4 enclosed" is the
     * distinction that makes the shortfall visible, and a boolean collapses it.
     */
    public record ChecklistItem(

        /** e.g. Discharge Summary, Final Itemised Bill, Implant Stickers. */
        @NotBlank @Size(max = 150) String name,

        @NotNull @PositiveOrZero Integer toBeSubmit,

        @NotNull @PositiveOrZero Integer submitted,

        /**
         * Why the shortfall exists — operational, e.g. "1 lost by attender".
         * Not a place for clinical detail; the field label says so in the UI.
         */
        @Size(max = 500) String nonSubmission
    ) {}
}
