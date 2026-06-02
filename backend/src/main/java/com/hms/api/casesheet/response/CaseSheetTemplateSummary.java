package com.hms.api.casesheet.response;

import com.hms.domain.casesheet.model.CaseSheetVisitType;
import java.util.UUID;

public record CaseSheetTemplateSummary(
    UUID id,
    String name,
    String specialization,
    CaseSheetVisitType visitType,
    String description,
    boolean defaultTemplate,
    int fieldCount
) {}
