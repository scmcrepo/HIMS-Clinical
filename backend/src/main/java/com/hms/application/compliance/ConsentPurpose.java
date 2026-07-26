package com.hms.application.compliance;

import java.util.Set;

/**
 * The consent vocabulary.
 *
 * <p>DPDP requires consent to be specific and informed, which means purposes must
 * be enumerated rather than lumped into a single "I agree". A patient consenting
 * to treatment has not consented to an automated system calling them, and has
 * certainly not consented to their claim being posted to an insurer.
 *
 * <p>Adding a purpose here is a compliance decision, not a code change: it needs
 * new notice text, a new notice version, and re-consent from patients who only
 * agreed to the old set.
 */
public enum ConsentPurpose {

    /** Core clinical care. Usually captured at registration. */
    TREATMENT("Treatment and clinical care", true),

    /** Automated WhatsApp or SMS contact about appointments. */
    AGENT_MESSAGING("Automated messaging about your care", false),

    /** Automated voice calls, including recording and transcription. */
    AGENT_VOICE("Automated voice calls, which may be recorded", false),

    /** Sharing claim data with an insurer or TPA. */
    INSURANCE_CLAIM("Sharing your details with your insurer for claims", false),

    /** Creating or linking a national health id. */
    ABHA_LINKAGE("Creating or linking your ABHA health account", false),

    /** Anything not required to deliver care. Never bundled with the above. */
    MARKETING("Updates and offers from the hospital", false);

    private final String noticeSummary;
    private final boolean requiredForCare;

    ConsentPurpose(String noticeSummary, boolean requiredForCare) {
        this.noticeSummary = noticeSummary;
        this.requiredForCare = requiredForCare;
    }

    public String getNoticeSummary() {
        return noticeSummary;
    }

    /**
     * Whether refusing this purpose blocks care.
     *
     * <p>Only TREATMENT is. Everything else must be genuinely optional — consent
     * conditioned on receiving treatment is not freely given and therefore is not
     * consent.
     */
    public boolean isRequiredForCare() {
        return requiredForCare;
    }

    /** Purposes an AI agent needs before it may act on a patient's behalf. */
    public static final Set<ConsentPurpose> AGENT_PURPOSES =
        Set.of(AGENT_MESSAGING, AGENT_VOICE);
}
