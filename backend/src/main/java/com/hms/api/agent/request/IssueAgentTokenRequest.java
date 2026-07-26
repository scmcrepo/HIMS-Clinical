package com.hms.api.agent.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;
import java.util.UUID;

/**
 * @param branchId     null for a tenant-wide token; set to pin the agent to one location
 * @param validityDays null defaults to 90; capped at a year because a credential
 *                     that never rotates is one nobody notices has leaked
 */
public record IssueAgentTokenRequest(
    @NotBlank String name,
    @NotEmpty Set<String> scopes,
    UUID branchId,
    @Min(1) @Max(365) Integer validityDays
) {
}
