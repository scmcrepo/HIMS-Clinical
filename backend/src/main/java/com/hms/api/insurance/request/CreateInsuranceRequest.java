package com.hms.api.insurance.request;

import com.hms.domain.insurance.model.InsurancePreAuthType;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Manual insurance policy registration — Screen 1.3.
 *
 * <p>The fallback when nothing could be retrieved digitally. Either a policy
 * number or a member/card id identifies the policy to the payer; requiring both
 * would block the common case of a health card that shows only a member id, so
 * the pairing is validated in the service rather than with field annotations.
 */
public record CreateInsuranceRequest(
    UUID patientId,
    UUID billId,
    UUID encounterId,
    @NotBlank String insurerName,
    String policyNumber,
    /** Member / card id from the health card. */
    String memberId,
    /** Third-party administrator, where one handles the claim. */
    String tpaName,
    /** INDIVIDUAL | FAMILY_FLOATER | PM_JAY | GROUP */
    String policyType,
    InsurancePreAuthType preAuthType,
    String communication
) {}
