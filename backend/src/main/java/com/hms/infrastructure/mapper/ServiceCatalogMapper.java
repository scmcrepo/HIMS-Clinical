package com.hms.infrastructure.mapper;

import com.hms.api.catalog.response.ServiceCategoryResponse;
import com.hms.api.catalog.response.ServiceItemResponse;
import com.hms.domain.catalog.model.PricingTier;
import com.hms.domain.catalog.model.ServiceCategory;
import com.hms.domain.catalog.model.ServiceCatalogItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ServiceCatalogMapper {

    ServiceItemResponse toResponse(ServiceCatalogItem item);

    @Mapping(target = "id", source = "id")
    ServiceItemResponse.PricingTierResponse toPricingTierResponse(PricingTier tier);

    List<ServiceItemResponse.PricingTierResponse> toPricingTierResponses(List<PricingTier> tiers);

    ServiceCategoryResponse toCategoryResponse(ServiceCategory category);

    List<ServiceCategoryResponse> toCategoryResponses(List<ServiceCategory> categories);
}
