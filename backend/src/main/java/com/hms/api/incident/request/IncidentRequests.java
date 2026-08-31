package com.hms.api.incident.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request bodies for the incident register — WO-026. */
public final class IncidentRequests {

    private IncidentRequests() {}

    public record Raise(
        @NotBlank
        @Pattern(regexp = "CROSS_TENANT_ACCESS|UNAUTHORISED_ACCESS|DATA_LOSS|DATA_EXPOSURE"
                        + "|CREDENTIAL_COMPROMISE|INTEGRITY_COMPROMISE|AVAILABILITY|OTHER",
                 message = "unknown incident category")
        String category,

        @NotBlank
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,

        @NotBlank @Size(max = 500) String summary,

        /** Free text. Must not contain personal data — ids go in the affected list. */
        String detail,

        @Size(max = 300) String dataCategories,

        /**
         * When we became aware, if earlier than now. Both statutory clocks run
         * from this, so backdating a late-filed incident is honest rather than
         * optional.
         */
        Instant detectedAt,

        /** True when the blast radius is not yet known. Never defaults to false silently. */
        boolean scopeUncertain
    ) {}

    public record AffectedPatients(
        @NotNull @Size(min = 1, message = "list at least one affected patient")
        List<UUID> patientIds
    ) {}

    public record Contain(@NotBlank String remediation) {}

    public record BoardNotification(
        /** The Board's acknowledgement reference — the only durable proof of filing. */
        String boardReference,
        /** False for the initial intimation, true for the fuller 72-hour report. */
        boolean detailReport
    ) {}

    public record NotifyPrincipals(
        @NotBlank @Pattern(regexp = "EMAIL|SMS|WHATSAPP|POST|IN_PERSON") String channel
    ) {}

    public record Close(@NotBlank String rootCause) {}

    public record Dismiss(
        @NotBlank(message = "a dismissal needs a reason — it is the record of why "
                          + "someone decided this was not a breach")
        String reason
    ) {}
}
