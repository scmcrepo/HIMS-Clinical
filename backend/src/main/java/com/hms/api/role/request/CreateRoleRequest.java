package com.hms.api.role.request;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;
public record CreateRoleRequest(
    @NotBlank @Size(min=2, max=50) String name,
    String description,
    @NotNull Set<UUID> featureIds
) {}
