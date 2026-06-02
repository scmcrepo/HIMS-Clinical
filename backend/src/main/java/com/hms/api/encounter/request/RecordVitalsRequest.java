package com.hms.api.encounter.request;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
public record RecordVitalsRequest(@NotNull Map<String, Object> vitals) {}
