package com.hms.api.prefix.request;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.billing.model.SequenceResetPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record CreateSequenceGeneratorRequest(
    @NotBlank String prefixString,
    @NotNull DocumentType documentType,
    @NotNull SequenceResetPolicy resetPolicy
) {}
