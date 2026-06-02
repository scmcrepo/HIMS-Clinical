package com.hms.api.opip.request;

import java.time.Instant;
import java.util.UUID;

public record AdmissionReferralRequest(
    UUID encounterId,
    String reason,
    String adviceToPatient,
    String instructionsToNurses,
    Instant admissionDate
) {}
