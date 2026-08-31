package com.hms.api.retention.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Request bodies for retention administration — WO-025. */
public final class RetentionRequests {

    private RetentionRequests() {}

    /**
     * Change a policy. Every field optional; only what is sent is applied.
     *
     * <p>{@code dryRun=false} is the field that arms a policy to destroy records.
     * The service refuses it if the policy does not currently validate, and logs
     * it at WARN with the acting user.
     */
    public record Update(
        @Min(value = 1, message = "retentionDays must be positive")
        Integer retentionDays,

        Boolean enabled,

        Boolean dryRun,

        @Min(1) @Max(10000) Integer maxRowsPerRun,

        /** Why this period. Shown to anyone auditing why a record is gone. */
        String justification
    ) {}
}
