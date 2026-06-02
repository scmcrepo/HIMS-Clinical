package com.hms.api.diagnostic.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record RecordResultRequest(
    @NotNull UUID lineId,
    @NotBlank String resultValue,
    String resultUnit,
    String referenceRange
) {}
