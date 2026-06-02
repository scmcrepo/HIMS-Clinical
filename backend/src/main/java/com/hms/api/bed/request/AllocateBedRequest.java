package com.hms.api.bed.request;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
public record AllocateBedRequest(
    @NotNull UUID bedId,
    @NotNull UUID encounterId,
    UUID consultantId,
    UUID billId,
    LocalDate admissionDate,
    String billType,
    UUID payorId
) {}
