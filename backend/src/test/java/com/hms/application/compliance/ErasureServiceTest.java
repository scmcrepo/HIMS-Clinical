package com.hms.application.compliance;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-011 / P-003.
 *
 * <p>The hard part of erasure is not deleting a row, it is knowing where the
 * copies are. This test exists to fail loudly when someone adds a table holding
 * patient data and forgets the erasure registry — at which point erasure would
 * silently miss it and the hospital would be reporting compliance it has not
 * achieved.
 *
 * <p>When this test fails, the fix is almost never to edit the expected set. It
 * is to add the new store to {@code ErasureService.TARGETS} with a deliberate
 * strategy.
 */
class ErasureServiceTest {

    /**
     * Every table that holds a patient_id or patient-derived content.
     *
     * <p>Keep this in step with the migrations. V176 added the agent tables,
     * V177 the HITL queue, V178 ABHA and NHCX.
     */
    private static final Set<String> STORES_HOLDING_PATIENT_DATA = Set.of(
        "agent_idempotency_keys",   // cached tool responses may contain patient detail
        "hitl_escalations",         // transcripts are PHI
        "abha_linkages",            // health identifiers
        "agent_tool_invocations",   // surrogate ids linking runs to a patient
        "nhcx_transactions",        // payer response bundles carry clinical detail
        "consent_records");         // retained deliberately, but must be considered

    @Test
    void everyStoreHoldingPatientDataIsInTheErasureRegistry() {
        Set<String> registered = ErasureService.registeredStores();
        for (String store : STORES_HOLDING_PATIENT_DATA) {
            assertTrue(registered.contains(store),
                store + " holds patient data but is not in ErasureService.TARGETS. "
                + "An erasure request would silently miss it, which is a DPDP "
                + "compliance failure rather than a bug.");
        }
    }

    @Test
    void theRegistryContainsNothingUnexpected() {
        // A store listed here but not holding patient data suggests a copy-paste
        // that will delete the wrong thing.
        for (String registered : ErasureService.registeredStores()) {
            assertTrue(STORES_HOLDING_PATIENT_DATA.contains(registered),
                registered + " is in the erasure registry but is not documented as "
                + "holding patient data. Either document it or remove it.");
        }
    }

    @Test
    void consentRecordsAreProcessedLastSoTheAuditTrailSurvivesAPartialSweep() {
        // Ordering is deliberate: derived copies first, the consent audit trail
        // last, so a failure part-way through never destroys the evidence that
        // consent existed.
        var ordered = ErasureService.registeredStores().stream().toList();
        assertTrue(ordered.indexOf("consent_records") == ordered.size() - 1,
            "consent_records must be swept last; it is the audit trail");
    }
}
