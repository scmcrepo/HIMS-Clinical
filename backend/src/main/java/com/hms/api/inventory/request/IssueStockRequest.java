package com.hms.api.inventory.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
public record IssueStockRequest(
    @NotNull UUID sourceDepartmentId,
    @NotNull UUID targetDepartmentId,
    @NotNull @Size(min=1) List<IssueLine> lines
) {
    public record IssueLine(@NotNull UUID batchId, @Positive int quantity) {}
}
