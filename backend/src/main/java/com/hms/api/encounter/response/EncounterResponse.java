package com.hms.api.encounter.response;
import com.hms.domain.billing.model.EncounterType;
import com.hms.domain.encounter.model.*;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
public record EncounterResponse(
    UUID id, UUID patientId, String patientNumber, String patientName,
    UUID primaryProviderId, UUID appointmentId,
    EncounterType encounterType, EncounterStatus status, VisitMode visitMode,
    Instant startedAt, LocalTime checkedInAt, Instant dischargedAt,
    String diagnosis,
    boolean hasBed, boolean hasDraftBill, boolean cancelled,
    Instant casesheetRecordedAt,
    Map<String, Object> vitalData, Map<String, Object> consultantShareMap
) {}
