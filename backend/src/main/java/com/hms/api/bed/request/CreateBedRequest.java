package com.hms.api.bed.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import com.hms.domain.shared.model.EntityStatus;
public record CreateBedRequest(
    @NotBlank String name,
    @NotNull UUID roomCategoryId,
    EntityStatus status
) {}

