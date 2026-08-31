package com.hms.api.grievance.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request bodies for grievance redressal — WO-027. */
public final class GrievanceRequests {

    private GrievanceRequests() {}

    /**
     * Record a grievance.
     *
     * <p>Both {@code patientId} and {@code complainantContact} are optional
     * individually; the service requires at least one. Someone complaining that
     * you hold their data wrongly may well not appear in your records the way
     * you expect, and refusing to log the complaint until they do would be a
     * tidy way of never recording the inconvenient ones.
     */
    public record Raise(
        UUID patientId,

        @Size(max = 300) String complainantContact,

        @NotBlank
        @Pattern(regexp = "CONSENT|ACCESS_REQUEST|CORRECTION|ERASURE|DATA_ACCURACY"
                        + "|UNAUTHORISED_USE|SERVICE|OTHER",
                 message = "unknown grievance category")
        String category,

        @NotBlank
        @Pattern(regexp = "PORTAL|IN_PERSON|EMAIL|PHONE|POST|WHATSAPP")
        String channel,

        @NotBlank @Size(max = 200) String subject,

        /** The complaint in the person's own words. Encrypted at rest. */
        String body
    ) {}

    public record Acknowledge(String note) {}

    public record Assign(UUID assignee) {}

    public record Note(
        @NotBlank String note,
        /** True when the complainant was told about this step, not just the file updated. */
        boolean communicated
    ) {}

    public record Resolve(
        @NotBlank(message = "a resolution must say what was decided — this is what "
                          + "the complainant will be told")
        String resolution
    ) {}

    public record Escalate(@NotBlank String boardReference) {}

    public record LinkIncident(UUID incidentId) {}

    public record Withdraw(String reason) {}

    /** Publish or replace the tenant's contact point. */
    public record PublishContact(
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 120) String designation,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 30) String phone,
        String postalAddress,
        /**
         * True only where the tenant has determined it is a Significant Data
         * Fiduciary. This is a legal claim with obligations attached, not a job
         * title.
         */
        boolean isDpo,
        boolean basedInIndia
    ) {}
}
