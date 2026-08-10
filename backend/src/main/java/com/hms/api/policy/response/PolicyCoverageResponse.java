package com.hms.api.policy.response;

import com.hms.infrastructure.persistence.policy.PolicyCoverageEntity;
import com.hms.infrastructure.persistence.policy.PolicyExclusionEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The coverage and benefit picture for Screen 2.1.
 *
 * <p>Amounts stay in <b>paise</b> all the way to the browser. Formatting to
 * rupees is a presentation concern; converting server-side would mean two
 * rounding points instead of one, and the frontend already holds the currency
 * helpers.
 *
 * <p>Nulls are meaningful and are not replaced with zero: a null room-rent cap
 * means the payer stated no cap, which is not the same as a cap of nothing.
 */
public record PolicyCoverageResponse(
    UUID id,
    String policyStatus,
    Long sumInsuredPaise,
    Long utilisedPaise,
    Long balancePaise,
    Long roomRentCapPaise,
    Long icuCapPaise,
    Long deductiblePaise,
    String roomCategory,
    Integer coPayBasisPoints,
    Integer pedWaitingMonths,
    Boolean pedWaitingSatisfied,
    Instant checkedAt,
    List<ExclusionResponse> exclusions
) {

    public record ExclusionResponse(String kind, String code, String description, Long limitPaise) {
        public static ExclusionResponse from(PolicyExclusionEntity e) {
            return new ExclusionResponse(e.getKind(), e.getCode(), e.getDescription(),
                                         e.getLimitPaise());
        }
    }

    public static PolicyCoverageResponse from(PolicyCoverageEntity c,
                                              List<PolicyExclusionEntity> ex) {
        return new PolicyCoverageResponse(
            c.getId(), c.getPolicyStatus(),
            c.getSumInsuredPaise(), c.getUtilisedPaise(), c.getBalancePaise(),
            c.getRoomRentCapPaise(), c.getIcuCapPaise(), c.getDeductiblePaise(),
            c.getRoomCategory(), c.getCoPayBasisPoints(),
            c.getPedWaitingMonths(), c.getPedWaitingSatisfied(),
            c.getCheckedAt(),
            ex == null ? List.of() : ex.stream().map(ExclusionResponse::from).toList());
    }
}
