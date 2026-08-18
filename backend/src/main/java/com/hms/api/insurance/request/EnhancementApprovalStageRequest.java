package com.hms.api.insurance.request;

import com.hms.domain.insurance.model.ModeOfCommunication;
import com.hms.domain.insurance.model.TpaDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/** Stage 4 — the TPA's decision on the enhancement request (WO-020). */
public record EnhancementApprovalStageRequest(

    @NotNull TpaDecision approvalStatus,

    Instant dateOfApproval,

    ModeOfCommunication communicationByTpa,

    /**
     * Revised sanctioned total in paise. Required when APPROVED. Stored beside
     * the original pre-auth limit rather than over it, so a short-paid claim can
     * still be compared against what was first agreed.
     */
    @Positive Long approvedLimit,

    /** Required when REJECTED. Encrypted at rest. */
    String rejectionReason
) {}
