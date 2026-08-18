package com.hms.api.insurance.response;

import com.hms.domain.insurance.model.CourierVendor;
import com.hms.domain.insurance.model.InsurancePreAuthType;
import com.hms.domain.insurance.model.InsuranceWorkflowStage;
import com.hms.domain.insurance.model.ModeOfCommunication;
import com.hms.domain.insurance.model.ModeOfDispatch;
import com.hms.domain.insurance.model.TpaDecision;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The full desk view of one claim — every stage in one payload (WO-020).
 *
 * <p>One response rather than seven endpoints because the timeline modal renders
 * all stages at once and lets the clerk click between them; seven round trips
 * to draw one screen would be slower and would let the stages disagree with each
 * other mid-render.
 *
 * <p>All amounts in paise. Decrypted values (claimNo, the reason fields) are
 * present here because the caller holds the INSURANCE feature — the same gate
 * that protects the policy number this endpoint has always returned.
 */
public record InsuranceDeskResponse(

    UUID id,
    UUID patientId,
    UUID billId,
    UUID encounterId,
    String insurerName,
    String tpaName,
    String policyNumber,
    String memberId,
    String policyType,

    /** Null on records created before the desk workflow existed. */
    InsuranceWorkflowStage currentStage,
    String currentStageLabel,
    InsuranceStageTimestamps stageTimestamps,

    /** Whether a bill is linked — the gate on raising an enhancement. */
    boolean billLinked,

    /** True when cardValidity is in the past. Computed server-side so the desk and the reports agree. */
    boolean cardExpired,

    /** Enhanced sanction where approved, else the pre-auth sanction, else null. */
    Long effectiveApprovedLimit,

    // ── Stage 1
    LocalDate cardValidity,
    InsurancePreAuthType preAuthType,
    ModeOfCommunication preauthCommunicationToTpa,
    String preauthFaxNo,
    String preauthMailId,
    Instant preauthAppliedDate,
    Long preauthRequestedAmount,

    // ── Stage 2
    String claimNo,
    TpaDecision preauthApprovalStatus,
    Instant preauthDateOfApproval,
    ModeOfCommunication preauthCommunicationByTpa,
    String preauthApproveFaxNo,
    String preauthApproveMailId,
    Long preauthApprovedLimit,
    String preauthRejectionReason,

    // ── Stage 3
    InsurancePreAuthType enhancementType,
    Instant enhancementAppliedDate,
    Long enhancementRequestedAmount,
    ModeOfCommunication enhancementCommunicationToTpa,
    String enhancementFaxNo,
    String enhancementMailId,
    String reasonForEnhancement,

    // ── Stage 4
    TpaDecision enhancementApprovalStatus,
    Instant enhancementDateOfApproval,
    ModeOfCommunication enhancementCommunicationByTpa,
    Long enhancementApprovedLimit,
    String enhancementRejectionReason,

    // ── Stage 5
    Map<String, Object> checklist,

    // ── Stage 6
    ModeOfDispatch modeOfDispatch,
    CourierVendor courier,
    Instant dispatchDate,
    String dispatchedBy,
    String dispatchMailId,
    String podNo,
    String reasonForDelay,

    // ── Stage 7
    List<InsuranceChequeResponse> cheques,
    /** Sum of cheque amounts, paise. Computed here so every caller agrees. */
    Long totalReceived
) {}
