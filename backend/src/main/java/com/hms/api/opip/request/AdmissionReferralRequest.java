package com.hms.api.opip.request;

import java.util.UUID;

public record AdmissionReferralRequest(
    UUID encounterId,
    String reason,
    String adviceToPatient,
    String instructionsToNurses,
    String admissionDate
) {}
