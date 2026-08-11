package com.hms.application.abdm;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * When an ABDM consent artifact actually authorises a read.
 *
 * <p>Deliberately dependency-free — no Spring, no persistence, no clock
 * singleton — so the rules can be exercised in isolation. This is the code that
 * stands between the hospital and reading another provider's records without
 * authority, and it is small enough that there is no excuse for it being
 * uncertain.
 *
 * <p>{@link Instant} is passed in rather than read from {@code Instant.now()}
 * inside these methods. A time-dependent rule that reads the clock internally
 * can only be tested by waiting, so in practice it does not get tested at the
 * boundaries — which is precisely where expiry bugs live.
 */
public final class ConsentArtifactRules {

    private ConsentArtifactRules() {
    }

    public static final String GRANTED = "GRANTED";
    public static final String EXPIRED = "EXPIRED";
    public static final String REVOKED = "REVOKED";

    /** ABDM purpose-of-request codes. */
    public static final Set<String> PURPOSE_CODES =
        Set.of("CAREMGT", "BTG", "PUBHLTH", "HPAYMT", "DSRCH", "PATRQT");

    /** ABDM health-information types. */
    public static final Set<String> HI_TYPES = Set.of(
        "DiagnosticReport", "Prescription", "DischargeSummary", "OPConsultation",
        "ImmunizationRecord", "HealthDocumentRecord", "WellnessRecord");

    /**
     * Whether an artifact permits a fetch right now.
     *
     * <p>Revocation beats everything, including an expiry date still in the
     * future: a patient who withdraws consent has withdrawn it, and treating a
     * later expiry as the governing date would keep reading against their
     * explicit refusal.
     *
     * <p>A missing expiry is treated as <b>not</b> valid. An artifact without an
     * end date is either a parse failure or a malformed grant, and defaulting
     * that to perpetual permission is the wrong direction to fail in.
     */
    public static boolean permitsFetch(String state, Instant expiresAt, Instant revokedAt,
                                       Instant now) {
        if (revokedAt != null && !revokedAt.isAfter(now)) {
            return false;
        }
        if (REVOKED.equals(state)) {
            return false;
        }
        if (!GRANTED.equals(state)) {
            return false;
        }
        if (expiresAt == null) {
            return false;
        }
        return expiresAt.isAfter(now);
    }

    /**
     * The state an artifact should be showing, given the clock.
     *
     * <p>Derived rather than trusted from the stored column, because an artifact
     * stored as GRANTED goes stale on its own the moment its expiry passes and
     * nothing writes to the row to say so.
     */
    public static String effectiveState(String storedState, Instant expiresAt, Instant revokedAt,
                                        Instant now) {
        if (revokedAt != null && !revokedAt.isAfter(now)) {
            return REVOKED;
        }
        if (REVOKED.equals(storedState)) {
            return REVOKED;
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            return EXPIRED;
        }
        return storedState;
    }

    /**
     * Whether a record's clinical date falls inside the consented range.
     *
     * <p>Both bounds inclusive: a patient consenting to "1 Jan to 31 Mar" means
     * those days are included, and an exclusive upper bound would silently drop
     * the last day's records.
     *
     * <p>A record with no date is <b>excluded</b>. It cannot be shown to fall
     * within what the patient allowed, and the burden is on the record.
     */
    public static boolean withinConsentedRange(LocalDate recordDate, LocalDate from, LocalDate to) {
        if (recordDate == null) {
            return false;
        }
        if (from != null && recordDate.isBefore(from)) {
            return false;
        }
        return to == null || !recordDate.isAfter(to);
    }

    /** Whether the artifact covers this health-information type. */
    public static boolean coversHiType(Set<String> consentedTypes, String hiType) {
        return hiType != null && consentedTypes != null && consentedTypes.contains(hiType);
    }

    public static boolean isValidPurpose(String purposeCode) {
        return purposeCode != null && PURPOSE_CODES.contains(purposeCode);
    }

    public static boolean areValidHiTypes(Set<String> types) {
        return types != null && !types.isEmpty() && HI_TYPES.containsAll(types);
    }

    /**
     * Whether a requested date range is sane before it reaches the gateway.
     *
     * <p>A future "from" date asks for records that cannot exist yet, which the
     * Consent Manager will accept and the patient will be asked to approve for
     * nothing.
     */
    public static boolean isValidRange(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null || to == null) {
            return false;
        }
        if (from.isAfter(to)) {
            return false;
        }
        return !from.isAfter(today);
    }
}
