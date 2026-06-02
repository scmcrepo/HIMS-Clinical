package com.hms.api.casesheet.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record FieldRequest(
    @NotBlank String fieldKey,
    @NotBlank String label,
    @NotBlank String fieldType,
    String section,
    @Min(0) int displayOrder,
    boolean required,
    String placeholder,
    String helpText,
    List<Map<String, String>> options,
    Map<String, Object> validation,
    String defaultValue,
    boolean visible
) {}
