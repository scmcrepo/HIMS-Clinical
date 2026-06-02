package com.hms.api.insurance.response;
import com.hms.domain.insurance.model.InsurancePreAuthType;
import com.hms.domain.insurance.model.InsuranceStatus;
import java.time.LocalDate;
import java.util.UUID;
public record InsuranceResponse(
    UUID id, UUID patientId, UUID billId, UUID encounterId,
    String insurerName, String policyNumber,
    InsurancePreAuthType preAuthType, String preAuthNumber,
    Long preAuthAmount, LocalDate preAuthDate,
    String communication, InsuranceStatus insuranceStatus,
    String rejectionReason
) {}
