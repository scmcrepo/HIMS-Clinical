package com.hms.infrastructure.mapper;

import com.hms.api.inventory.response.InventoryBatchResponse;
import com.hms.domain.inventory.model.InventoryBatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "id",           source = "batch.id")
    @Mapping(target = "itemId",       source = "batch.itemId")
    @Mapping(target = "departmentId", source = "batch.departmentId")
    @Mapping(target = "isExpired",    expression = "java(batch.isExpired())")
    @Mapping(target = "isOutOfStock", expression = "java(batch.isOutOfStock())")
    @Mapping(target = "taxRate",      source = "taxRate")
    @Mapping(target = "supplierId",   source = "supplierId")
    InventoryBatchResponse toResponse(InventoryBatch batch, String itemName, String departmentName, BigDecimal taxRate, java.util.UUID supplierId);
}
