package com.hms.api.insurance.request;

import com.hms.domain.insurance.model.InsurancePreAuthType;
import com.hms.domain.insurance.model.ModeOfCommunication;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Stage 1 — the pre-authorisation request sent to the TPA (WO-020).
 *
 * <p>The fax-number / mail-id pairing is validated in the service rather than
 * with field annotations, because which one is mandatory depends on
 * {@link #communicationToTpa} and bean validation cannot express that
 * conditional without a class-level constraint that reads worse than the check
 * it replaces.
 */
public record PreauthStageRequest(

    /** Health card member id, as printed. */
    @Size(max = 50) String cardNo,

    /**
     * Card expiry. Not rejected when it is in the past — the desk needs to
     * record what the patient presented, and an expired card is a warning for a
     * human, not a validation failure.
     */
    LocalDate cardValidity,

    @Size(max = 80) String policyNumber,

    InsurancePreAuthType preAuthType,

    @NotNull ModeOfCommunication communicationToTpa,

    /** Required when communicationToTpa is FAX. */
    @Size(max = 80) String faxNo,

    /** Required when communicationToTpa is MAIL. */
    @Size(max = 150) String mailId,

    /** When the request went out. Defaults to now if omitted. */
    Instant appliedDate,

    /** Estimated hospitalisation cost sent for sanction, in paise. */
    @Positive Long requestedAmount
) {}
