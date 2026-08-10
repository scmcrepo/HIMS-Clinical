package com.hms.api.abha.response;

import com.hms.infrastructure.persistence.abha.AbhaLinkageEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * An ABHA linkage as the front desk sees it.
 *
 * <p>The ABHA number is <b>masked</b>. Screen 1.1 needs to show the desk that a
 * linkage succeeded and let them recognise the right record; it does not need
 * the full 14 digits, and sending them to a browser puts a national identifier
 * into logs, screenshots and support tickets. The full value stays encrypted at
 * rest and is released only through the card-download endpoint, which is
 * separately permissioned and audited.
 */
public record AbhaLinkageResponse(
    UUID id,
    UUID patientId,
    String abhaNumberMasked,
    String abhaAddress,
    String linkageState,
    Instant linkedAt,
    String failureCode
) {

    public static AbhaLinkageResponse from(AbhaLinkageEntity e) {
        return new AbhaLinkageResponse(
            e.getId(),
            e.getPatientId(),
            mask(e.getAbhaNumber()),
            e.getAbhaAddress(),
            e.getLinkageState(),
            e.getLinkedAt(),
            e.getFailureCode());
    }

    /** {@code 91234567890123} becomes {@code XX-XXXX-XXXX-0123}. */
    static String mask(String abhaNumber) {
        if (abhaNumber == null || abhaNumber.length() < 4) {
            return null;
        }
        return "XX-XXXX-XXXX-" + abhaNumber.substring(abhaNumber.length() - 4);
    }
}
