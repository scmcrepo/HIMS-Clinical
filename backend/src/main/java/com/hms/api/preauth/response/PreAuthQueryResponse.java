package com.hms.api.preauth.response;

import com.hms.infrastructure.persistence.preauth.PreAuthQueryEntity;

import java.time.Instant;
import java.util.UUID;

/** One round of the insurer query thread — Screen 4.2 / 4.3. */
public record PreAuthQueryResponse(
    UUID id,
    Integer roundNumber,
    Instant raisedAt,
    String queryCode,
    String queryText,
    Instant respondedAt,
    String responseText,
    boolean answered
) {
    public static PreAuthQueryResponse from(PreAuthQueryEntity e) {
        return new PreAuthQueryResponse(e.getId(), e.getRoundNumber(), e.getRaisedAt(),
                                        e.getQueryCode(), e.getQueryText(), e.getRespondedAt(),
                                        e.getResponseText(), e.getRespondedAt() != null);
    }
}
