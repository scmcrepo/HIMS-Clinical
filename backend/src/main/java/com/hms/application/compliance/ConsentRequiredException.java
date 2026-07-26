package com.hms.application.compliance;

import lombok.Getter;

/**
 * Raised when an action needs consent the patient has not given.
 *
 * <p>Distinct from a generic validation failure so callers can respond
 * appropriately: an agent should stop and hand to a human who can ask for
 * consent, not retry, and not silently skip the step.
 */
@Getter
public class ConsentRequiredException extends RuntimeException {

    private final ConsentPurpose purpose;

    public ConsentRequiredException(ConsentPurpose purpose) {
        super("Patient has not consented to: " + purpose.getNoticeSummary());
        this.purpose = purpose;
    }
}
