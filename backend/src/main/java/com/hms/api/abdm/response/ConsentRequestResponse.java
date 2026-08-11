package com.hms.api.abdm.response;

import com.hms.infrastructure.persistence.abdm.AbdmConsentRequestEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A consent request as Screen 3.1's status list shows it. */
public record ConsentRequestResponse(
    UUID id,
    String requestState,
    String purposeCode,
    String hiTypes,
    LocalDate dateRangeFrom,
    LocalDate dateRangeTo,
    Instant expiresAt,
    Instant createdAt
) {
    public static ConsentRequestResponse from(AbdmConsentRequestEntity e) {
        return new ConsentRequestResponse(e.getId(), e.getRequestState(), e.getPurposeCode(),
                                          e.getHiTypes(), e.getDateRangeFrom(), e.getDateRangeTo(),
                                          e.getExpiresAt(), e.getCreatedAt());
    }
}
