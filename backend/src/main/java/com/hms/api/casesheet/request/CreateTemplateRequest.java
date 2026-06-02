package com.hms.api.casesheet.request;

import com.hms.domain.casesheet.model.CaseSheetVisitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateTemplateRequest(
    @NotBlank String name,
    @NotBlank String specialization,
    @NotNull  CaseSheetVisitType visitType,
    String description,
    boolean defaultTemplate,
    @Valid List<FieldRequest> fields
) {}
