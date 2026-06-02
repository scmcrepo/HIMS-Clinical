package com.hms.api.casesheet.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FieldResponse(
    UUID id,
    String fieldKey,
    String label,
    String fieldType,
    String section,
    int displayOrder,
    boolean required,
    String placeholder,
    String helpText,
    List<Map<String, String>> options,
    Map<String, Object> validation,
    String defaultValue,
    boolean visible
) {}
