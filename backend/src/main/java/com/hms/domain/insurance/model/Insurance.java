package com.hms.domain.insurance.model;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import com.hms.security.encryption.PiiField;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Insurance record linking a patient's insurance policy to a bill or encounter.
 *
 * PII: policyNumber, preAuthNumber — encrypted as insurance identifiers.
 *
 * Pre-auth workflow:
 *   PRE_AUTH_REQUESTED → PRE_AUTH_RECEIVED → SETTLED / REJECTED
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

    @Enumerated(EnumType.STRING)
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
