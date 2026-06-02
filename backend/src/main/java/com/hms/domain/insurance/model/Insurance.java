package com.hms.domain.insurance.model;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Insurance record linking a patient's insurance policy to a bill or encounter.
 *
 * Pre-auth workflow:
 *   PRE_AUTH_REQUESTED → PRE_AUTH_RECEIVED → SETTLED / REJECTED
 *
 * The communicationType maps to legacy InsuranceCommunication enum:
 *   EMAIL, PHONE, LETTER, PORTAL, OTHER
 */
@Entity
@Table(name = "insurances", indexes = {
    @Index(name = "idx_ins_patient",   columnList = "patient_id"),
    @Index(name = "idx_ins_bill",      columnList = "bill_id"),
    @Index(name = "idx_ins_encounter", columnList = "encounter_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Insurance extends AuditableEntity {

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "insurer_name", length = 150)
    private String insurerName;

    @Column(name = "policy_number", length = 80)
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "pre_auth_type", length = 40)
    private InsurancePreAuthType preAuthType;

    @Column(name = "pre_auth_number", length = 80)
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

    // ── Behaviour ─────────────────────────────────────────────────────────

    public void receivePreAuth(String preAuthNumber, long amount, LocalDate receivedDate) {
        this.preAuthNumber = preAuthNumber;
        this.preAuthAmount = amount;
        this.preAuthDate   = receivedDate;
        this.insuranceStatus = InsuranceStatus.PRE_AUTH_RECEIVED;
    }

    public void reject(String reason) {
        this.rejectionReason  = reason;
        this.insuranceStatus  = InsuranceStatus.REJECTED;
    }

    public void settle() {
        if (insuranceStatus == InsuranceStatus.REJECTED) {
            throw new com.hms.exception.BusinessRuleViolationException(
                "Cannot settle a rejected insurance record");
        }
        this.insuranceStatus = InsuranceStatus.SETTLED;
    }
}
