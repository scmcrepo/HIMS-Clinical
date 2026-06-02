package com.hms.api.prefix.response;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.billing.model.SequenceResetPolicy;
import java.time.LocalDate;
import java.util.UUID;
public record SequenceGeneratorResponse(
    UUID id,
    String prefixString,
    DocumentType documentType,
    SequenceResetPolicy resetPolicy,
    boolean activated,
    long currentCounter,
    Short currentFiscalYear,
    LocalDate activatedAt,
    LocalDate deactivatedAt
) {}
