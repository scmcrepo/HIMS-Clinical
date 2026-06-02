package com.hms.api.encounter.request;
import com.hms.domain.encounter.model.EncounterStatus;
import java.util.Map;
import java.util.UUID;
public record UpdateEncounterRequest(
    String diagnosis,

    EncounterStatus status,
    Map<String, Object> vitalData
) {}
