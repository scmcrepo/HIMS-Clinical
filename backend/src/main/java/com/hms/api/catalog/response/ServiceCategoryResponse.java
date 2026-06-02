package com.hms.api.catalog.response;
import com.hms.domain.catalog.model.ServiceCategoryType;
import java.util.UUID;
public record ServiceCategoryResponse(UUID id, String name, ServiceCategoryType categoryType) {}
