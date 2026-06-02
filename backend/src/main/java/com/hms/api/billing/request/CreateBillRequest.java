package com.hms.api.billing.request;
import com.hms.domain.billing.model.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
public record CreateBillRequest(
    @NotNull UUID patientId,
    @NotNull BillType billType,
    @NotNull EncounterType encounterType,
    UUID primaryProviderId,
    UUID encounterId,
    UUID payorId,
    UUID referralId,
    Instant admissionAt
) {}
