package com.hms.api.insurance.request;
import com.hms.domain.insurance.model.InsurancePreAuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record CreateInsuranceRequest(
    UUID patientId,
    UUID billId,
    UUID encounterId,
    @NotBlank String insurerName,
    String policyNumber,
    InsurancePreAuthType preAuthType,
    String communication
) {}
