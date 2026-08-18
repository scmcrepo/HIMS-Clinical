package com.hms.infrastructure.mapper;

import com.hms.api.catalog.response.ServiceCategoryResponse;
import com.hms.api.catalog.response.ServiceItemResponse;
import com.hms.domain.billing.model.BillType;
import com.hms.domain.catalog.model.PricingTier;
import com.hms.domain.catalog.model.ServiceCatalogItem;
import com.hms.domain.catalog.model.ServiceCategory;
import com.hms.domain.catalog.model.ServiceCategoryType;
import com.hms.domain.catalog.model.ServiceType;
import com.hms.domain.shared.model.EntityStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-18T10:08:26+0530",
    comments = "version: 1.6.2, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class ServiceCatalogMapperImpl implements ServiceCatalogMapper {

    @Override
    public ServiceItemResponse toResponse(ServiceCatalogItem item) {
        if ( item == null ) {
            return null;
        }

        UUID id = null;
        String name = null;
        UUID categoryId = null;
        ServiceType serviceType = null;
        boolean requiresOrder = false;
        EntityStatus status = null;
        List<ServiceItemResponse.PricingTierResponse> pricingTiers = null;

        id = item.getId();
        name = item.getName();
        categoryId = item.getCategoryId();
        serviceType = item.getServiceType();
        requiresOrder = item.isRequiresOrder();
        status = item.getStatus();
        pricingTiers = toPricingTierResponses( item.getPricingTiers() );

        ServiceItemResponse serviceItemResponse = new ServiceItemResponse( id, name, categoryId, serviceType, requiresOrder, status, pricingTiers );

        return serviceItemResponse;
    }

    @Override
    public ServiceItemResponse.PricingTierResponse toPricingTierResponse(PricingTier tier) {
        if ( tier == null ) {
            return null;
        }

        UUID id = null;
        BillType billType = null;
        long unitRate = 0L;

        id = tier.getId();
        billType = tier.getBillType();
        unitRate = tier.getUnitRate();

        ServiceItemResponse.PricingTierResponse pricingTierResponse = new ServiceItemResponse.PricingTierResponse( id, billType, unitRate );

        return pricingTierResponse;
    }

    @Override
    public List<ServiceItemResponse.PricingTierResponse> toPricingTierResponses(List<PricingTier> tiers) {
        if ( tiers == null ) {
            return null;
        }

        List<ServiceItemResponse.PricingTierResponse> list = new ArrayList<ServiceItemResponse.PricingTierResponse>( tiers.size() );
        for ( PricingTier pricingTier : tiers ) {
            list.add( toPricingTierResponse( pricingTier ) );
        }

        return list;
    }

    @Override
    public ServiceCategoryResponse toCategoryResponse(ServiceCategory category) {
        if ( category == null ) {
            return null;
        }

        UUID id = null;
        String name = null;
        ServiceCategoryType categoryType = null;

        id = category.getId();
        name = category.getName();
        categoryType = category.getCategoryType();

        ServiceCategoryResponse serviceCategoryResponse = new ServiceCategoryResponse( id, name, categoryType );

        return serviceCategoryResponse;
    }

    @Override
    public List<ServiceCategoryResponse> toCategoryResponses(List<ServiceCategory> categories) {
        if ( categories == null ) {
            return null;
        }

        List<ServiceCategoryResponse> list = new ArrayList<ServiceCategoryResponse>( categories.size() );
        for ( ServiceCategory serviceCategory : categories ) {
            list.add( toCategoryResponse( serviceCategory ) );
        }

        return list;
    }
}
