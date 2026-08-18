package com.hms.api.insurance.request;

import com.hms.domain.insurance.model.InsurancePreAuthType;
import com.hms.domain.insurance.model.ModeOfCommunication;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Stage 3 — a mid-stay enhancement request (WO-020).
 *
 * <p>Rejected with 409 unless a bill is already linked: an enhancement asks the
 * TPA for more money against charges that must be evidenced, and without a bill
 * there is nothing to evidence them with.
 *
 * <p>This is the manual, faxed enhancement. The NHCX-native equivalent is
 * {@code PreAuthService.requestEnhancement}, which posts a FHIR bundle and waits
 * for a callback. The two are deliberately separate records — see WO-020 D-2.
 */
public record EnhancementStageRequest(

    InsurancePreAuthType enhancementType,

    Instant appliedDate,

    /** Revised total requested from the TPA, in paise. */
    @NotNull @Positive Long requestedAmount,

    @NotNull ModeOfCommunication communicationToTpa,

    @Size(max = 80)  String faxNo,
    @Size(max = 150) String mailId,

    /**
     * Clinical justification. Mandatory — an enhancement with no stated reason
     * is one the TPA will query, and the query costs more time than the field.
     * Encrypted at rest.
     */
    @NotBlank String reasonForEnhancement
) {}
