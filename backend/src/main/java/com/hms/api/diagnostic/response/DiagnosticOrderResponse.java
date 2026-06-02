package com.hms.api.diagnostic.response;
import com.hms.domain.diagnostic.model.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record DiagnosticOrderResponse(
    UUID id, UUID encounterId, UUID patientId, UUID providerId,
    DiagnosticType diagnosticType, String sequenceNumber,
    LocalDate orderDate, DiagnosticPaymentStatus paymentStatus, DiagnosticTestStatus testStatus, boolean billed,
    String patientName, String patientNumber, String patientGender, String patientAge,
    List<DiagnosticOrderLineResponse> lines
) {}
