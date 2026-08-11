package com.hms.api.abdm.response;

import com.hms.infrastructure.persistence.abdm.ExternalHealthRecordEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * One external record in the viewer's index.
 *
 * <p>The payload is <b>omitted</b>. A list of thirty records should not ship
 * thirty decrypted clinical bundles to a browser when the clinician will open
 * one — and each open is separately audited, which a bulk list would bypass.
 * Fetch the body through the single-record endpoint.
 */
public record ExternalRecordResponse(
    UUID id,
    String hiType,
    Instant recordDate,
    String sourceHipName,
    String displayTitle,
    boolean imported
) {
    public static ExternalRecordResponse from(ExternalHealthRecordEntity e) {
        return new ExternalRecordResponse(e.getId(), e.getHiType(), e.getRecordDate(),
                                          e.getSourceHipName(), e.getDisplayTitle(),
                                          e.getImportedAt() != null);
    }
}
