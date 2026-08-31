package com.hms.application.compliance;

/**
 * Where a consent record came from, and therefore whether it can be relied on.
 *
 * <p>This is a different question from {@code captureChannel}, which records
 * <em>where</em> consent was taken (in person, WhatsApp, web). Provenance records
 * <em>who decided</em> it existed. A row can have
 * {@code captureChannel = VERBAL_IN_PERSON} and still be worthless if no human
 * was actually involved — which is exactly the state this enum was introduced to
 * describe.
 */
public enum ConsentProvenance {

    /**
     * A named, authenticated user attested that the patient was shown the notice
     * and agreed. {@code capturedBy} is non-null and points at that user.
     */
    STAFF_ATTESTED,

    /** The patient agreed themselves, through the portal or mobile app. */
    PATIENT_DIGITAL,

    /**
     * Written by the pre-V205 defect in which {@code AbhaService},
     * {@code PolicyDiscoveryService} and {@code PreAuthService} granted the
     * consent they were about to check.
     *
     * <p><b>This is not consent.</b> No patient was asked and no human attested.
     * The rows are retained because deleting them would destroy the record that
     * the system once asserted consent — which is the first thing an
     * investigation asks about — but {@link ConsentService#hasConsent} treats
     * them as absent, so affected patients are re-asked at next contact.
     */
    SYSTEM_INFERRED,

    /** Migrated from a prior system that carried its own evidence trail. */
    IMPORTED;

    /**
     * Whether a grant with this provenance may be relied on to permit processing.
     *
     * <p>The single place this judgement is made. Adding a provenance without
     * deciding this deliberately is how an invalid grant quietly becomes a valid
     * one.
     */
    public boolean isReliable() {
        return this != SYSTEM_INFERRED;
    }
}
