package com.hms.api.bed.response;
import com.hms.domain.bed.model.BedStatus;
import com.hms.domain.shared.model.EntityStatus;
import java.util.UUID;

public record BedResponse(
    UUID id,
    String name,
    UUID roomCategoryId,
    String roomCategoryName,
    BedStatus bedStatus,
    EntityStatus status,
    String allocatedPatientName,
    String allocatedPatientNumber,
    UUID allocatedEncounterId,
    String allocatedConsultantName
) {}
