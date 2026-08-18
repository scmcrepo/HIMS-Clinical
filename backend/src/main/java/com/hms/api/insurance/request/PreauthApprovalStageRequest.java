package com.hms.api.insurance.request;

import com.hms.domain.insurance.model.ModeOfCommunication;
import com.hms.domain.insurance.model.TpaDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Stage 2 — the TPA's decision on the pre-authorisation (WO-020).
 *
 * <p>{@code approvedLimit} is mandatory when APPROVED and
 * {@code rejectionReason} when REJECTED; both are conditional, so both are
 * checked in the service.
 */
public record PreauthApprovalStageRequest(

    /** The TPA's own claim docket number. Encrypted at rest. */
    @NotBlank @Size(min = 3, max = 50) String claimNo,

    @NotNull TpaDecision approvalStatus,

    Instant dateOfApproval,

    ModeOfCommunication communicationByTpa,

    @Size(max = 80)  String approveFaxNo,
    @Size(max = 150) String approveMailId,

    /** Sanctioned amount in paise. Required when APPROVED. */
    @Positive Long approvedLimit,

    /** Required when REJECTED. Encrypted at rest — it is clinical free text. */
    String rejectionReason
) {}
