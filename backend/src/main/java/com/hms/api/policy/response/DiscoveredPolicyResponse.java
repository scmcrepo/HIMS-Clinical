package com.hms.api.policy.response;

import com.hms.infrastructure.persistence.policy.DiscoveredPolicyEntity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A discovered policy for Screen 1.2's results list.
 *
 * <p>Policy number and member id are masked. The desk needs to recognise which
 * policy is which, not to transcribe the identifiers; the full values stay
 * encrypted and are released to the payer in the claim bundle, not to a browser.
 */
public record DiscoveredPolicyResponse(
    UUID id,
    String payerName,
    String tpaName,
    String policyNumberMasked,
    String memberIdMasked,
    String policyType,
    LocalDate policyStartDate,
    LocalDate policyEndDate,
    String primaryInsuredName,
    String relationship,
    boolean linked
) {

    public static DiscoveredPolicyResponse from(DiscoveredPolicyEntity e) {
        return new DiscoveredPolicyResponse(
            e.getId(),
            e.getPayerName(),
            e.getTpaName(),
            maskTail(e.getPolicyNumber()),
            maskTail(e.getMemberId()),
            e.getPolicyType(),
            e.getPolicyStartDate(),
            e.getPolicyEndDate(),
            e.getPrimaryInsuredName(),
            e.getRelationship(),
            e.getLinkedInsuranceId() != null);
    }

    /** Keeps the last four characters, e.g. {@code ****6789}. */
    static String maskTail(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }
}
