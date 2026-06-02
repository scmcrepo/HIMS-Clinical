package com.hms.api.casesheet.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CaseSheetRecordResponse(
    UUID id,
    UUID encounterId,
    CaseSheetTemplateSummary template,
    Map<String, Object> data,
    UUID recordedBy,
    Instant recordedAt,
    Instant modifiedAt
) {}
