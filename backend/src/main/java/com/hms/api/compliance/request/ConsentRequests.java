package com.hms.api.compliance.request;

import com.hms.application.compliance.ConsentPurpose;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Request bodies for the consent management surface — WO-023. */
public final class ConsentRequests {

    private ConsentRequests() {}

    /**
     * Capture consent from a consent form, outside the mid-action 409 path.
     *
     * <p>No {@code capturedBy}: the capturing user is read from the session. A
     * client that could name its own capturer could name anyone, which would make
     * the field worthless as evidence.
     *
     * <p>No notice text either. The client says which version it displayed; the
     * server hashes the text it holds for that version. A client supplying both
     * would be attesting to its own claim.
     */
    public record Grant(
        @NotNull(message = "purpose is required") ConsentPurpose purpose,

        @NotBlank(message = "noticeVersion is required") String noticeVersion,

        @NotBlank(message = "noticeLanguage is required") String noticeLanguage,

        @Pattern(regexp = "IN_PERSON|PORTAL|WHATSAPP|VOICE|PAPER_FORM",
                 message = "unknown capture channel")
        String captureChannel,

        /**
         * Must be true. False is a validation failure rather than a silent no-op,
         * so "the patient declined" is never mistaken for "the checkbox was never
         * wired up".
         */
        @AssertTrue(message = "The patient must be shown the notice and agree")
        boolean patientAgreed,

        boolean minor,

        boolean guardianVerified
    ) {}

    /**
     * Withdraw consent.
     *
     * <p>Note how little is required: a purpose, and optionally where the request
     * came from. There is no reason field and no confirmation flag, because
     * consent that is harder to withdraw than to give is not freely given.
     */
    public record Withdraw(
        @NotNull(message = "purpose is required") ConsentPurpose purpose,

        @Pattern(regexp = "IN_PERSON|PORTAL|WHATSAPP|VOICE|PAPER_FORM|STAFF_PORTAL",
                 message = "unknown withdrawal channel")
        String channel
    ) {}
}
