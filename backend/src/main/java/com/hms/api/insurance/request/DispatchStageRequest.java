package com.hms.api.insurance.request;

import com.hms.domain.insurance.model.CourierVendor;
import com.hms.domain.insurance.model.ModeOfDispatch;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Stage 6 — physical or electronic dispatch of the claim docket (WO-020).
 *
 * <p>COURIER requires a vendor and a POD number; EMAIL requires a destination
 * mail id. Checked in the service, since the requirement is conditional on
 * {@link #modeOfDispatch}.
 */
public record DispatchStageRequest(

    @NotNull ModeOfDispatch modeOfDispatch,

    /** Required when modeOfDispatch is COURIER. */
    CourierVendor courier,

    /**
     * Consignment tracking number. Required for COURIER — it is the only proof
     * of delivery the hospital will have if the TPA denies receiving the docket.
     */
    @Size(max = 100) String podNo,

    /** Required when modeOfDispatch is EMAIL. */
    @Size(max = 150) String dispatchMailId,

    Instant dispatchDate,

    /** Staff member who handed the docket over. */
    @Size(max = 150) String dispatchedBy,

    /** Justification when dispatch slipped past the agreed turnaround. */
    String reasonForDelay
) {}
