package com.hms.api.bed.response;

import java.util.UUID;

/**
 * Lightweight projection returned by GET /beds/search-inpatients.
 * Used by the Bed Management UI to allow staff to search patients
 * by patient number (SCMCP-XXXX), name, or phone number instead of raw UUID.
 */
public record InpatientSearchResult(
    UUID encounterId,
    String patientNumber,
    String patientName,
    UUID patientId,
    String contactNumber
) {}
