package com.hms.api.opip.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record AddNurseNoteRequest(
    @NotBlank String notes,
    Instant   noteAt,         // defaults to now if null
    UUID      requestedById   // consultant/nurse who recorded; optional
) {}
