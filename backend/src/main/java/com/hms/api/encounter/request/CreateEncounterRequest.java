package com.hms.api.encounter.request;
import com.hms.domain.encounter.model.VisitMode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record CreateEncounterRequest(
    @NotNull UUID patientId,
    @NotNull UUID primaryProviderId,
    UUID appointmentId,
    VisitMode visitMode
) {}
