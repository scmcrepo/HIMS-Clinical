package com.hms.api.catalog.response;
import com.hms.domain.billing.model.BillType;
import com.hms.domain.catalog.model.ServiceType;
import com.hms.domain.shared.model.EntityStatus;
import java.util.List;
import java.util.UUID;
public record ServiceItemResponse(
    UUID id, String name, UUID categoryId,
    ServiceType serviceType, boolean requiresOrder, EntityStatus status,
    List<PricingTierResponse> pricingTiers
) {
    public record PricingTierResponse(UUID id, BillType billType, long unitRate) {}
}
