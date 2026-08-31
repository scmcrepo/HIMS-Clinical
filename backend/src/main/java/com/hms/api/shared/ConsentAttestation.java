package com.hms.api.shared;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * A staff member's attestation that a patient was shown a notice and agreed.
 *
 * <p>Lives in {@code api/shared} because three unrelated modules — ABHA
 * enrolment, policy discovery and pre-auth — need the identical shape, and three
 * copies would drift.
 *
 * <p>Deliberately absent: {@code capturedBy}. The capturing user comes from the
 * security context, never from the request body. A client that could name its own
 * capturer could name anyone, which would make {@code captured_by} worthless as
 * evidence.
 *
 * <p>Also deliberately absent: the notice text. The client says which version it
 * displayed; the server hashes the text it holds for that version. A client that
 * supplied both would be attesting to its own claim.
 */
public record ConsentAttestation(

    @NotBlank(message = "noticeVersion is required")
    String noticeVersion,

    @NotBlank(message = "noticeLanguage is required")
    String noticeLanguage,

    /**
     * Must be true. A false value is a validation failure rather than a silent
     * no-op, so "the patient declined" is never mistaken for "the box was not
     * wired up".
     */
    @AssertTrue(message = "The patient must be shown the notice and agree before proceeding")
    boolean patientAgreed,

    boolean minor,

    boolean guardianVerified
) {}
