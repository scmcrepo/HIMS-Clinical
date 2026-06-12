package com.hms.api.encounter.response;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.encounter.model.EncounterStatus;
import java.time.Instant;
import java.util.UUID;
public record EncounterSummaryResponse(
    UUID id, UUID patientId, String patientNumber, String patientName, String patientMobileNumber,
    String patientGender, String patientAge,
    UUID primaryProviderId, String providerName,
    EncounterType encounterType, EncounterStatus status,
    Instant startedAt, Instant dischargedAt,
    String diagnosis, boolean hasBed, boolean hasDraftBill,
    String bedName,
    java.util.Map<String, Object> consultantShareMap
) {}
