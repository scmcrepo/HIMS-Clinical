package com.hms.api.casesheet.response;

import com.hms.domain.shared.model.EntityStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DischargeTemplateDetail(
    UUID id,
    String name,
    String specialization,
    String description,
    boolean defaultTemplate,
    List<FieldResponse> fields,
    Instant createdAt,
    Instant modifiedAt,
    EntityStatus status
) {}
