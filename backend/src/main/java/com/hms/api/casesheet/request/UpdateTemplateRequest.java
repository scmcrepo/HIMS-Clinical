package com.hms.api.casesheet.request;

import com.hms.domain.casesheet.model.CaseSheetVisitType;
import jakarta.validation.Valid;
import java.util.List;

public record UpdateTemplateRequest(
    String name,
    String specialization,
    CaseSheetVisitType visitType,
    String description,
    Boolean defaultTemplate,
    @Valid List<FieldRequest> fields
) {}
