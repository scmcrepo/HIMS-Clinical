package com.hms.infrastructure.persistence.policy;

import com.hms.domain.shared.model.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A point-in-time coverage and benefit snapshot — Screen 2.1.
 *
 * <p>Every check writes a new row rather than updating the last one. The
 * hospital admits a patient on the strength of a specific answer about room
 * eligibility and co-pay; if the payer later disputes the claim, the record of
 * what was said at admission is the hospital's evidence. Overwriting destroys it.
 *
 * <p><b>All money is in paise.</b> The co-pay is in basis points, not percent,
 * because retail policies carry values like 7.5% that an integer percentage
 * cannot hold and a float would round badly across thousands of claims.
 */
@Entity
@Table(name = "patient_policy_coverages")
@Getter
@Setter
public class PolicyCoverageEntity extends AuditableEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "insurance_id")
    private UUID insuranceId;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "payer_code", nullable = false, length = 60)
    private String payerCode;

    /** ACTIVE | EXPIRED | LAPSED | SUSPENDED | UNKNOWN */
    @Column(name = "policy_status", nullable = false, length = 20)
    private String policyStatus = "UNKNOWN";

    @Column(name = "sum_insured_paise")
    private Long sumInsuredPaise;

    @Column(name = "utilised_paise")
    private Long utilisedPaise;

    @Column(name = "balance_paise")
    private Long balancePaise;

    @Column(name = "room_rent_cap_paise")
    private Long roomRentCapPaise;

    @Column(name = "icu_cap_paise")
    private Long icuCapPaise;

    @Column(name = "deductible_paise")
    private Long deductiblePaise;

    /** Payers send a category, an amount, or both. */
    @Column(name = "room_category", length = 120)
    private String roomCategory;

    /** 10% is 1000. */
    @Column(name = "co_pay_basis_points")
    private Integer coPayBasisPoints;

    @Column(name = "ped_waiting_months")
    private Integer pedWaitingMonths;

    @Column(name = "ped_waiting_satisfied")
    private Boolean pedWaitingSatisfied;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt = Instant.now();
}
