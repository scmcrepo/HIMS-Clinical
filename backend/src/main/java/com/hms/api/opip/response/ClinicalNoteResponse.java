package com.hms.api.opip.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic response for Progress Notes and Nurse Notes.
 * Both share the same shape — they differ only in their URL path.
 */
public record ClinicalNoteResponse(
    UUID    id,
    UUID    encounterId,
    String  notes,
    Instant noteAt,
    UUID    requestedById,
    String  requestedByName,   // resolved display name of the consultant/nurse
    Instant createdAt
) {}
