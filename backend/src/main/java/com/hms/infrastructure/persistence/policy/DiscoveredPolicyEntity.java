package com.hms.infrastructure.persistence.policy;

import com.hms.domain.shared.model.AuditableEntity;
import com.hms.security.encryption.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A policy the payer says this patient holds — Screen 1.2.
 *
 * <p>Deliberately separate from {@code Insurance}. This row is the payer's
 * assertion, arriving from an NHCX discovery response; an {@code Insurance} row
 * is a policy the hospital has accepted and will bill against. Collapsing the
 * two would let an unverified discovery result silently become a billable
 * policy. {@link #linkedInsuranceId} records the moment a human bridged them.
 *
 * <p>Policy number and member id identify a person to their insurer, so both are
 * encrypted with blind-index tokens beside them.
 */
@Entity
@Table(name = "discovered_policies")
@Getter
@Setter
public class DiscoveredPolicyEntity extends AuditableEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Ties this result back to the discovery request that produced it. */
    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "payer_code", nullable = false, length = 60)
    private String payerCode;

    @Column(name = "payer_name", length = 160)
    private String payerName;

    @Column(name = "tpa_name", length = 160)
    private String tpaName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "policy_number")
    private String policyNumber;

    @Column(name = "policy_number_token", length = 64)
    private String policyNumberToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "member_id")
    private String memberId;

    @Column(name = "member_id_token", length = 64)
    private String memberIdToken;

    /** INDIVIDUAL | FAMILY_FLOATER | PM_JAY | GROUP */
    @Column(name = "policy_type", length = 24)
    private String policyType;

    @Column(name = "policy_start_date")
    private LocalDate policyStartDate;

    @Column(name = "policy_end_date")
    private LocalDate policyEndDate;

    /**
     * On a family floater the primary insured is often not the patient, so the
     * desk needs the name to confirm they are looking at the right household.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "primary_insured_name")
    private String primaryInsuredName;

    /** SELF | SPOUSE | CHILD | PARENT | OTHER */
    @Column(name = "relationship", length = 20)
    private String relationship;

    @Column(name = "linked_insurance_id")
    private UUID linkedInsuranceId;

    @Column(name = "linked_at")
    private Instant linkedAt;
}
