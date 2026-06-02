package com.hms.api.shared;
import java.util.List;
public record ValidationErrorResponse(String message, List<FieldError> errors) {}
