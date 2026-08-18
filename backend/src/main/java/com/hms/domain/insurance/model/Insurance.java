package com.hms.domain.insurance.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Insurance record linking a patient's insurance policy to a bill or encounter.
 *
 * PII: policyNumber, preAuthNumber, memberId, claimNo — encrypted as insurance
 * identifiers. The three free-text reason fields are also encrypted: they
 * explain why treatment cost what it did, which makes them clinical
 * information (WO-020 decision D-7).
 *
 * <p>Two workflows live on this row and they are not the same thing:
 *
 * <p><b>1. {@link InsuranceStatus}</b> — the original flat lifecycle
 * (ACTIVE → PRE_AUTH_REQUESTED → PRE_AUTH_RECEIVED → SETTLED / REJECTED). Still
 * drives {@code /insurance/pending} and the pre-existing screens. Unchanged.
 *
 * <p><b>2. {@link InsuranceWorkflowStage}</b> — the seven-stage manual TPA desk
 * flow added by WO-020, for insurers not reachable over NHCX. Null on every row
 * created before that work order, which the UI reads as "legacy record".
 *
 * <p>All amounts are in <b>paise</b>, as everywhere else in this codebase.
 */
@Entity
@Table(name = "insurances", indexes = {
    @Index(name = "idx_ins_patient",   columnList = "patient_id"),
    @Index(name = "idx_ins_bill",      columnList = "bill_id"),
    @Index(name = "idx_ins_encounter", columnList = "encounter_id")
})
@Getter @Setter @NoArgsConstructor
@org.hibernate.annotations.Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@org.hibernate.annotations.Filter(name = "branchFilter", condition = "branch_id = :branchId")
public class Insurance extends AuditableEntity {

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "insurer_name", length = 150)
    private String insurerName;

    @PiiField(category = PiiField.PiiCategory.INSURANCE_ID, description = "Patient insurance policy number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "policy_number", length = 512)
    private String policyNumber;

    /**
     * Member / card id printed on the health card — Screen 1.3.
     *
     * <p>Encrypted, with a blind-index token beside it, because it identifies
     * the patient to their insurer. Many health cards show only this and no
     * policy number, so it is an alternative identifier rather than an extra.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "member_id")
    private String memberId;

    @Column(name = "member_id_token", length = 64)
    private String memberIdToken;

    /** Third-party administrator handling the claim, where one is involved. */
    @Column(name = "tpa_name", length = 160)
    private String tpaName;

    /** INDIVIDUAL | FAMILY_FLOATER | PM_JAY | GROUP */
    @Column(name = "policy_type", length = 24)
    private String policyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_auth_type", length = 40)
    private InsurancePreAuthType preAuthType;

    @PiiField(category = PiiField.PiiCategory.INSURANCE_ID, description = "Pre-authorisation reference number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pre_auth_number", length = 512)
    private String preAuthNumber;

    @Column(name = "pre_auth_amount")
    private Long preAuthAmount;

    @Column(name = "pre_auth_date")
    private LocalDate preAuthDate;

    @Column(name = "communication", length = 40)
    private String communication;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_status", length = 30)
    private InsuranceStatus insuranceStatus = InsuranceStatus.ACTIVE;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // ─────────────────────────────────────────────────────────────────────────
    //  Manual TPA desk workflow (WO-020). Everything below is null on records
    //  created before that work order.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Where the claim currently sits in the seven-stage desk flow.
     * Null means a legacy record predating the flow — not "stage zero".
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_current_status", length = 50)
    private InsuranceWorkflowStage insuranceCurrentStatus;

    // ── Stage 1: pre-auth request ───────────────────────────────────────────

    /**
     * Expiry printed on the health card. Compared against today at the desk so
     * a patient is not admitted on cashless terms against a lapsed card.
     */
    @Column(name = "card_validity")
    private LocalDate cardValidity;

    @Enumerated(EnumType.STRING)
    @Column(name = "preauth_communication_to_tpa", length = 40)
    private ModeOfCommunication preauthCommunicationToTpa;

    /** The TPA's fax number — the insurer's business contact, not patient data. */
    @Column(name = "preauth_fax_no", length = 80)
    private String preauthFaxNo;

    /** The TPA's claims mailbox — the insurer's business contact, not patient data. */
    @Column(name = "preauth_mail_id", length = 150)
    private String preauthMailId;

    /**
     * When the request actually went out, to the minute. Distinct from the
     * date-only {@link #preAuthDate}: TPA turnaround is measured in hours and a
     * date cannot say whether the fax beat the 4pm cutoff.
     */
    @Column(name = "preauth_applied_date")
    private Instant preauthAppliedDate;

    /** Estimated hospitalisation cost sent for sanction, in paise. */
    @Column(name = "preauth_requested_amount")
    private Long preauthRequestedAmount;

    @Column(name = "preauth_created_by")   private UUID    preauthCreatedBy;
    @Column(name = "preauth_created_date") private Instant preauthCreatedDate;
    @Column(name = "preauth_updated_by")   private UUID    preauthUpdatedBy;
    @Column(name = "preauth_updated_date") private Instant preauthUpdatedDate;

    // ── Stage 2: pre-auth approval / rejection ──────────────────────────────

    /**
     * The TPA's own claim docket number. Not {@link #preAuthNumber} (ours) and
     * not {@link #policyNumber} (the master policy). Encrypted because it
     * identifies the patient to their insurer; the token beside it is what
     * makes it searchable, since ciphertext is non-deterministic.
     */
    @PiiField(category = PiiField.PiiCategory.INSURANCE_ID, description = "TPA claim docket number")
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "claim_no")
    private String claimNo;

    @Column(name = "claim_no_token", length = 64)
    private String claimNoToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "preauth_approval_status", length = 40)
    private TpaDecision preauthApprovalStatus;

    @Column(name = "preauth_date_of_approval")
    private Instant preauthDateOfApproval;

    @Enumerated(EnumType.STRING)
    @Column(name = "preauth_communication_by_tpa", length = 40)
    private ModeOfCommunication preauthCommunicationByTpa;

    @Column(name = "preauth_approve_fax_no", length = 80)
    private String preauthApproveFaxNo;

    @Column(name = "preauth_approve_mail_id", length = 150)
    private String preauthApproveMailId;

    /** Amount the TPA sanctioned up front, in paise. */
    @Column(name = "preauth_approved_limit")
    private Long preauthApprovedLimit;

    /**
     * Why the TPA declined. Encrypted: "pre-existing diabetic nephropathy, 
     * excluded under waiting period" is a diagnosis, not an administrative note.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "preauth_rejection_reason")
    private String preauthRejectionReason;

    @Column(name = "preauth_approval_created_by")   private UUID    preauthApprovalCreatedBy;
    @Column(name = "preauth_approval_created_date") private Instant preauthApprovalCreatedDate;
    @Column(name = "preauth_approval_updated_by")   private UUID    preauthApprovalUpdatedBy;
    @Column(name = "preauth_approval_updated_date") private Instant preauthApprovalUpdatedDate;

    // ── Stage 3: enhancement request ────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "enhancement_type", length = 40)
    private InsurancePreAuthType enhancementType;

    @Column(name = "enhancement_applied_date")
    private Instant enhancementAppliedDate;

    /** Revised total requested from the TPA, in paise. */
    @Column(name = "enhancement_requested_amount")
    private Long enhancementRequestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "enhancement_communication_to_tpa", length = 40)
    private ModeOfCommunication enhancementCommunicationToTpa;

    @Column(name = "enhancement_fax_no", length = 80)
    private String enhancementFaxNo;

    @Column(name = "enhancement_mail_id", length = 150)
    private String enhancementMailId;

    /**
     * Clinical justification for exceeding the sanctioned limit. Encrypted:
     * "extended ICU stay following post-operative sepsis" is patient health
     * information in free-text form.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "reason_for_enhancement")
    private String reasonForEnhancement;

    @Column(name = "enhancement_created_by")   private UUID    enhancementCreatedBy;
    @Column(name = "enhancement_created_date") private Instant enhancementCreatedDate;
    @Column(name = "enhancement_updated_by")   private UUID    enhancementUpdatedBy;
    @Column(name = "enhancement_updated_date") private Instant enhancementUpdatedDate;

    // ── Stage 4: enhancement approval / rejection ───────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "enhancement_approval_status", length = 40)
    private TpaDecision enhancementApprovalStatus;

    @Column(name = "enhancement_date_of_approval")
    private Instant enhancementDateOfApproval;

    @Enumerated(EnumType.STRING)
    @Column(name = "enhancement_communication_by_tpa", length = 40)
    private ModeOfCommunication enhancementCommunicationByTpa;

    /**
     * Revised sanctioned total, in paise. A separate column rather than an
     * overwrite of {@link #preauthApprovedLimit}: when a claim is short-paid the
     * first question is what was originally sanctioned, and overwriting destroys
     * the answer.
     */
    @Column(name = "enhancement_approved_limit")
    private Long enhancementApprovedLimit;

    /** Encrypted for the same reason as {@link #preauthRejectionReason}. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "enhancement_rejection_reason")
    private String enhancementRejectionReason;

    @Column(name = "enhancement_approval_created_by")   private UUID    enhancementApprovalCreatedBy;
    @Column(name = "enhancement_approval_created_date") private Instant enhancementApprovalCreatedDate;
    @Column(name = "enhancement_approval_updated_by")   private UUID    enhancementApprovalUpdatedBy;
    @Column(name = "enhancement_approval_updated_date") private Instant enhancementApprovalUpdatedDate;

    // ── Stage 5: pre-dispatch document checklist ────────────────────────────

    /**
     * Document manifest, shape
     * {@code {"checklists":[{"name","toBeSubmit","submitted","nonSubmission"}]}}.
     *
     * <p>JSONB rather than a child table: no money, no aggregation beyond
     * counting shortfalls, and the row shape is whatever the TPA asked for this
     * week. Holds document names and counts only — never clinical detail.
     */
    @Type(JsonType.class)
    @Column(name = "checklist", columnDefinition = "jsonb")
    private Map<String, Object> checklist = new LinkedHashMap<>();

    @Column(name = "check_list_created_by")   private UUID    checkListCreatedBy;
    @Column(name = "check_list_created_date") private Instant checkListCreatedDate;
    @Column(name = "check_list_updated_by")   private UUID    checkListUpdatedBy;
    @Column(name = "check_list_updated_date") private Instant checkListUpdatedDate;

    // ── Stage 6: dispatch ───────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_of_dispatch", length = 40)
    private ModeOfDispatch modeOfDispatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "courier", length = 80)
    private CourierVendor courier;

    @Column(name = "dispatch_date")
    private Instant dispatchDate;

    /** Staff member who handed the docket over. A colleague's name, not patient data. */
    @Column(name = "dispatched_by", length = 150)
    private String dispatchedBy;

    @Column(name = "dispatch_mail_id", length = 150)
    private String dispatchMailId;

    /**
     * Consignment tracking number. The only proof the hospital holds that the
     * docket reached the TPA, and the thing a denied-receipt dispute turns on.
     */
    @Column(name = "pod_no", length = 100)
    private String podNo;

    @Column(name = "reason_for_delay")
    private String reasonForDelay;

    @Column(name = "dispatch_created_by")   private UUID    dispatchCreatedBy;
    @Column(name = "dispatch_created_date") private Instant dispatchCreatedDate;

    // ── Stage 7: disallowance ───────────────────────────────────────────────
    //  Itemised deductions live on charge_line_items.disallowed_amount, written
    //  through BillingOperationsService — the one component that owns bill
    //  money. Cheque receipts are rows in insurance_cheque_receipts.

    @Column(name = "disallowance_created_by")   private UUID    disallowanceCreatedBy;
    @Column(name = "disallowance_created_date") private Instant disallowanceCreatedDate;

    // ── Behaviour ───────────────────────────────────────────────────────────

    /**
     * The limit currently in force: the enhanced sanction where the TPA approved
     * one, otherwise the original pre-auth sanction.
     *
     * <p>Returns null when nothing has been sanctioned yet, which is different
     * from zero — zero would read as "they sanctioned nothing", and the desk
     * needs to tell those apart.
     */
    public Long effectiveApprovedLimit() {
        if (enhancementApprovalStatus == TpaDecision.APPROVED && enhancementApprovedLimit != null) {
            return enhancementApprovedLimit;
        }
        if (preauthApprovalStatus == TpaDecision.APPROVED) {
            return preauthApprovedLimit;
        }
        return null;
    }

    /** True when a health card expiry is recorded and it is in the past. */
    public boolean isCardExpired(LocalDate asOf) {
        return cardValidity != null && asOf != null && cardValidity.isBefore(asOf);
    }

    /** An enhancement may only be raised once the claim is bound to a real bill. */
    public boolean isBillLinked() {
        return billId != null;
    }

    /**
     * Move the workflow forward, never backward. Editing an early stage after
     * dispatch must not resurrect the claim on the "awaiting submission"
     * worklist.
     */
    public void advanceStage(InsuranceWorkflowStage submitted) {
        this.insuranceCurrentStatus =
            InsuranceWorkflowStage.advance(this.insuranceCurrentStatus, submitted);
    }

    public void receivePreAuth(String preAuthNumber, long amount, LocalDate receivedDate) {
        this.preAuthNumber   = preAuthNumber;
        this.preAuthAmount   = amount;
        this.preAuthDate     = receivedDate;
        this.insuranceStatus = InsuranceStatus.PRE_AUTH_RECEIVED;
    }

    public void reject(String reason) {
        this.rejectionReason = reason;
        this.insuranceStatus = InsuranceStatus.REJECTED;
    }

    public void settle() {
        if (insuranceStatus == InsuranceStatus.REJECTED) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot settle a rejected insurance record");
        }
        this.insuranceStatus = InsuranceStatus.SETTLED;
    }
}
