package com.hms.application.compliance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-011 / P-003.
 *
 * <p>These assertions are small but they encode a legal position, not a
 * preference: consent conditioned on receiving treatment is not freely given and
 * therefore is not consent. If someone marks a second purpose as required for
 * care, that is a compliance decision that should not pass silently.
 */
class ConsentPurposeTest {

    @Test
    void onlyTreatmentIsRequiredForCare() {
        long required = java.util.Arrays.stream(ConsentPurpose.values())
            .filter(ConsentPurpose::isRequiredForCare)
            .count();
        assertEquals(1, required,
            "Only TREATMENT may be required for care. Making another purpose "
            + "mandatory means consent is conditioned on receiving treatment, "
            + "which under DPDP is not freely given and therefore not consent.");
        assertTrue(ConsentPurpose.TREATMENT.isRequiredForCare());
    }

    @Test
    void agentContactIsOptional() {
        assertFalse(ConsentPurpose.AGENT_MESSAGING.isRequiredForCare());
        assertFalse(ConsentPurpose.AGENT_VOICE.isRequiredForCare());
    }

    @Test
    void marketingIsNeverBundledWithCare() {
        assertFalse(ConsentPurpose.MARKETING.isRequiredForCare());
    }

    @Test
    void bothAgentChannelsAreCoveredByTheAgentPurposeSet() {
        // The supervisor's channel gate maps whatsapp -> AGENT_MESSAGING and
        // voice -> AGENT_VOICE; a purpose missing here would leave a channel
        // ungated.
        assertTrue(ConsentPurpose.AGENT_PURPOSES.contains(ConsentPurpose.AGENT_MESSAGING));
        assertTrue(ConsentPurpose.AGENT_PURPOSES.contains(ConsentPurpose.AGENT_VOICE));
    }

    @Test
    void everyPurposeHasNoticeText() {
        // A purpose with no notice summary cannot produce informed consent.
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            assertFalse(purpose.getNoticeSummary() == null
                        || purpose.getNoticeSummary().isBlank(),
                purpose + " has no notice summary; consent to it cannot be informed");
        }
    }
}
