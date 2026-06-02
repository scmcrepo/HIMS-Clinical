package com.hms.api.diagnostic.request;
import com.hms.domain.diagnostic.model.DiagnosticType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
public record PlaceOrderRequest(
    UUID encounterId,
    @NotNull UUID patientId,
    UUID providerId,
    @NotNull DiagnosticType diagnosticType,
    UUID billId,
    @NotNull @Size(min=1) List<OrderLineRequest> lines
) {
    public record OrderLineRequest(
        @NotNull UUID serviceCatalogItemId,
        String itemName,
        UUID specimenId,
        String instruction
    ) {}
}
