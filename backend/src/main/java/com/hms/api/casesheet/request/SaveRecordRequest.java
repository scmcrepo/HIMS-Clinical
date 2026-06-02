package com.hms.api.casesheet.request;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record SaveRecordRequest(
    UUID templateId,
    @NotNull Map<String, Object> data
) {}
