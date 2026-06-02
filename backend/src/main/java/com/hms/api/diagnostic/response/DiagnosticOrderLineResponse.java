package com.hms.api.diagnostic.response;
import com.hms.domain.diagnostic.model.DiagnosticPaymentStatus;
import com.hms.domain.diagnostic.model.DiagnosticTestStatus;
import java.time.Instant;
import java.util.UUID;
public record DiagnosticOrderLineResponse(
    UUID id, UUID serviceCatalogItemId, String itemName,
    UUID specimenId, String specimenName, String instruction, DiagnosticPaymentStatus paymentStatus, DiagnosticTestStatus testStatus,
    String resultValue, String resultUnit, String referenceRange,
    Instant resultRecordedAt, boolean hasResult
) {}
