package com.hms.application.abdm;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WO-014 / MC-002.
 *
 * <p>These rules stand between the hospital and reading another provider's
 * records without authority, so the tests concentrate on the boundaries: the
 * instant of expiry, revocation racing a live expiry date, and a missing expiry
 * being treated as no permission rather than perpetual permission.
 *
 * <p>The clock is injected rather than read inside the rules, which is what
 * makes these boundaries testable at all.
 */
class ConsentArtifactRulesTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");
    private static final Instant FUTURE = NOW.plusSeconds(86_400);
    private static final Instant PAST = NOW.minusSeconds(86_400);

    @Test
    void grantedWithFutureExpiryPermitsFetch() {
        assertTrue(ConsentArtifactRules.permitsFetch("GRANTED", FUTURE, null, NOW));
    }

    @Test
    void expiredArtifactDoesNotPermitFetch() {
        assertFalse(ConsentArtifactRules.permitsFetch("GRANTED", PAST, null, NOW));
    }

    @Test
    void expiryExactlyNowIsAlreadyExpired() {
        // The boundary. Half-open the other way would grant a final read after
        // the patient's permission ended.
        assertFalse(ConsentArtifactRules.permitsFetch("GRANTED", NOW, null, NOW));
    }

    @Test
    void revocationBeatsAFutureExpiry() {
        // A patient who withdraws consent has withdrawn it; treating the later
        // expiry as governing would keep reading against an explicit refusal.
        assertFalse(ConsentArtifactRules.permitsFetch("GRANTED", FUTURE, PAST, NOW));
        assertEquals("REVOKED",
            ConsentArtifactRules.effectiveState("GRANTED", FUTURE, PAST, NOW));
    }

    @Test
    void missingExpiryIsNotPerpetualPermission() {
        // An artifact with no end date is a malformed grant, and defaulting it
        // to forever fails in the wrong direction.
        assertFalse(ConsentArtifactRules.permitsFetch("GRANTED", null, null, NOW));
    }

    @Test
    void nonGrantedStatesNeverPermitFetch() {
        assertFalse(ConsentArtifactRules.permitsFetch("PENDING_APPROVAL", FUTURE, null, NOW));
        assertFalse(ConsentArtifactRules.permitsFetch("DENIED", FUTURE, null, NOW));
        assertFalse(ConsentArtifactRules.permitsFetch("REVOKED", FUTURE, null, NOW));
    }

    @Test
    void storedStateGoesStaleAndEffectiveStateCorrectsIt() {
        // Nothing writes to the row when an expiry passes, so the stored value
        // cannot be trusted on read.
        assertEquals("EXPIRED", ConsentArtifactRules.effectiveState("GRANTED", PAST, null, NOW));
        assertEquals("GRANTED", ConsentArtifactRules.effectiveState("GRANTED", FUTURE, null, NOW));
    }

    @Test
    void consentedRangeIsInclusiveAtBothEnds() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        assertTrue(ConsentArtifactRules.withinConsentedRange(from, from, to));
        // An exclusive upper bound would silently drop the last day's records.
        assertTrue(ConsentArtifactRules.withinConsentedRange(to, from, to));
        assertFalse(ConsentArtifactRules.withinConsentedRange(to.plusDays(1), from, to));
        assertFalse(ConsentArtifactRules.withinConsentedRange(from.minusDays(1), from, to));
    }

    @Test
    void undatedRecordsAreExcludedFromAConsentedRange() {
        // It cannot be shown to fall within what the patient allowed.
        assertFalse(ConsentArtifactRules.withinConsentedRange(
            null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)));
    }

    @Test
    void onlyAbdmPurposeCodesAreAccepted() {
        assertTrue(ConsentArtifactRules.isValidPurpose("CAREMGT"));
        assertTrue(ConsentArtifactRules.isValidPurpose("BTG"));
        assertFalse(ConsentArtifactRules.isValidPurpose("MARKETING"));
        assertFalse(ConsentArtifactRules.isValidPurpose(null));
    }

    @Test
    void onlyAbdmHealthInformationTypesAreAccepted() {
        assertTrue(ConsentArtifactRules.areValidHiTypes(
            Set.of("Prescription", "DiagnosticReport")));
        assertFalse(ConsentArtifactRules.areValidHiTypes(Set.of("Prescription", "Gossip")));
        assertFalse(ConsentArtifactRules.areValidHiTypes(Set.of()));
    }

    @Test
    void coversHiTypeRejectsAnythingOutsideTheGrant() {
        Set<String> consented = Set.of("Prescription");
        assertTrue(ConsentArtifactRules.coversHiType(consented, "Prescription"));
        // A HIP that over-shares is not authority to keep what it sent.
        assertFalse(ConsentArtifactRules.coversHiType(consented, "DischargeSummary"));
        assertFalse(ConsentArtifactRules.coversHiType(consented, null));
    }

    @Test
    void futureAndReversedRangesAreRejectedBeforeReachingTheGateway() {
        LocalDate today = LocalDate.of(2026, 8, 11);
        assertTrue(ConsentArtifactRules.isValidRange(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), today));
        assertFalse(ConsentArtifactRules.isValidRange(
            LocalDate.of(2026, 3, 31), LocalDate.of(2026, 1, 1), today));
        // Asking for records that cannot exist yet wastes the one approval
        // interaction the hospital gets with the patient.
        assertFalse(ConsentArtifactRules.isValidRange(
            today.plusDays(1), today.plusDays(2), today));
    }
}
