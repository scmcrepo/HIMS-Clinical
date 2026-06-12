package com.hms.api.casesheet.response;

import com.hms.domain.shared.model.EntityStatus;
import java.util.UUID;

public record DischargeTemplateSummary(
    UUID id,
    String name,
    String specialization,
    String description,
    boolean defaultTemplate,
    int fieldCount,
    EntityStatus status
) {}
