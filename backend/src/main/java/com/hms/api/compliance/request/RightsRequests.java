package com.hms.api.compliance.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/** Request bodies for the data-principal rights surface. */
public final class RightsRequests {

    private RightsRequests() {}

    /**
     * Raise an erasure or correction request.
     *
     * @param correctionPayload required for CORRECTION: which fields the patient
     *                          says are wrong and what they should say. Ignored
     *                          for ERASURE.
     */
    public record Raise(
        @NotNull(message = "patientId is required") UUID patientId,

        @NotBlank(message = "requestType is required")
        @Pattern(regexp = "ERASURE|CORRECTION",
                 message = "requestType must be ERASURE or CORRECTION")
        String requestType,

        @Pattern(regexp = "PORTAL|IN_PERSON|EMAIL|PHONE|POST",
                 message = "requestedVia must be PORTAL, IN_PERSON, EMAIL, PHONE or POST")
        String requestedVia,

        boolean requestedByPatient,

        Map<String, Object> correctionPayload
    ) {}

    /** Record that the requester was proved to be the patient. */
    public record Verify(
        @NotBlank(message = "method is required")
        @Pattern(regexp = "PORTAL_OTP|IN_PERSON_ID|ABHA_VERIFIED|REGISTERED_POST|STAFF_OVERRIDE",
                 message = "unknown verification method")
        String method
    ) {}

    /** Refuse a request. The reason is shown to the patient, so it is mandatory. */
    public record Reject(
        @NotBlank(message = "a refusal must carry a reason the patient will be shown")
        @Size(max = 500) String reason
    ) {}
}
