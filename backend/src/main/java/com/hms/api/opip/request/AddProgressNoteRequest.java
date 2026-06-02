package com.hms.api.opip.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public record AddProgressNoteRequest(
    @NotBlank String notes,
    Instant   noteAt,         // defaults to now if null
    UUID      requestedById   // consultant who recorded; optional
) {}
