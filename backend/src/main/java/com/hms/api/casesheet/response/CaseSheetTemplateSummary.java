package com.hms.api.casesheet.response;

import com.hms.domain.casesheet.model.CaseSheetVisitType;
import com.hms.domain.shared.model.EntityStatus;
import java.util.UUID;

public record CaseSheetTemplateSummary(
    UUID id,
    String name,
    String specialization,
    CaseSheetVisitType visitType,
    String description,
    boolean defaultTemplate,
    int fieldCount,
    EntityStatus status
) {}
