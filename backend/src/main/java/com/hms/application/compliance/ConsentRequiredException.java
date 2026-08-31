package com.hms.application.compliance;

import lombok.Getter;

/**
 * Raised when an action needs consent the patient has not given.
 *
 * <p>Distinct from a generic validation failure so callers can respond
 * appropriately: an agent should stop and hand to a human who can ask for
 * consent, not retry, and not silently skip the step.
 *
 * <p>Carries the notice the desk must show, so the 409 response is actionable in
 * one round trip rather than forcing the client to look the text up separately.
 * Notice text is hospital copy, never patient data, so returning it in an error
 * body is safe.
 */
@Getter
public class ConsentRequiredException extends RuntimeException {

    private final ConsentPurpose purpose;
    private final String noticeVersion;
    private final String noticeLanguage;
    private final String noticeText;

    /** Full form, used when a notice could be resolved for the tenant. */
    public ConsentRequiredException(ConsentPurpose purpose, String noticeVersion,
                                    String noticeLanguage, String noticeText) {
        super("Patient has not consented to: " + purpose.getNoticeSummary());
        this.purpose = purpose;
        this.noticeVersion = noticeVersion;
        this.noticeLanguage = noticeLanguage;
        this.noticeText = noticeText;
    }

    /**
     * Kept so call sites and tests written against the original single-argument
     * constructor still compile. The response then carries no notice text and
     * the client falls back to its own copy.
     */
    public ConsentRequiredException(ConsentPurpose purpose) {
        this(purpose, null, null, null);
    }
}
