package com.hms.api.insurance.response;

import java.time.Instant;

/**
 * When each stage was first recorded, for the timeline sidebar.
 *
 * <p>A null entry means the stage has not been worked. The UI uses these to
 * decide which steps show a completion timestamp, rather than inferring it from
 * the current stage — a claim can be at DISPATCH_ENTRY having skipped the
 * enhancement stages entirely, and inferring would draw them as done.
 */
public record InsuranceStageTimestamps(
    Instant preauth,
    Instant preauthApproval,
    Instant enhancement,
    Instant enhancementApproval,
    Instant checkList,
    Instant dispatch,
    Instant disallowance
) {}
