package com.hms.api.encounter.request;
import java.util.Map;
import java.util.UUID;
public record RecordCasesheetRequest(
    String chiefComplaint,
    String historyOfPresentIllness,
    String examination,
    String diagnosis,

    String plan,
    Map<String, Object> customFields
) {}
