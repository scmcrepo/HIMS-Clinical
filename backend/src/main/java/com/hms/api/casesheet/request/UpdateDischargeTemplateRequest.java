package com.hms.api.casesheet.request;

import com.hms.domain.shared.model.EntityStatus;
import jakarta.validation.Valid;
import java.util.List;

public record UpdateDischargeTemplateRequest(
    String name,
    String specialization,
    String description,
    Boolean defaultTemplate,
    EntityStatus status,
    @Valid List<FieldRequest> fields
) {}
