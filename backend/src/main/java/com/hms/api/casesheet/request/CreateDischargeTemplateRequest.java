package com.hms.api.casesheet.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateDischargeTemplateRequest(
    @NotBlank String name,
    @NotBlank String specialization,
    String description,
    boolean defaultTemplate,
    @Valid List<FieldRequest> fields
) {}
