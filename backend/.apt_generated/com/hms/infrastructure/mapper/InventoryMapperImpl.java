package com.hms.infrastructure.mapper;

import com.hms.api.inventory.response.InventoryBatchResponse;
import com.hms.domain.inventory.model.InventoryBatch;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-18T10:08:26+0530",
    comments = "version: 1.6.2, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class InventoryMapperImpl implements InventoryMapper {

    @Override
    public InventoryBatchResponse toResponse(InventoryBatch batch, String itemName, String departmentName, BigDecimal taxRate, UUID supplierId) {
        if ( batch == null && itemName == null && departmentName == null && taxRate == null && supplierId == null ) {
            return null;
        }

        UUID id = null;
        UUID itemId = null;
        UUID departmentId = null;
        String batchNumber = null;
        int currentQuantity = 0;
        int freeQuantity = 0;
        BigDecimal purchaseRate = null;
        BigDecimal maximumRetailPrice = null;
        BigDecimal sellingRate = null;
        LocalDate expiryDate = null;
        if ( batch != null ) {
            id = batch.getId();
            itemId = batch.getItemId();
            departmentId = batch.getDepartmentId();
            batchNumber = batch.getBatchNumber();
            currentQuantity = batch.getCurrentQuantity();
            freeQuantity = batch.getFreeQuantity();
            purchaseRate = batch.getPurchaseRate();
            maximumRetailPrice = batch.getMaximumRetailPrice();
            sellingRate = batch.getSellingRate();
            expiryDate = batch.getExpiryDate();
        }
        String itemName1 = null;
        itemName1 = itemName;
        String departmentName1 = null;
        departmentName1 = departmentName;
        BigDecimal taxRate1 = null;
        taxRate1 = taxRate;
        UUID supplierId1 = null;
        supplierId1 = supplierId;

        boolean isExpired = batch.isExpired();
        boolean isOutOfStock = batch.isOutOfStock();

        InventoryBatchResponse inventoryBatchResponse = new InventoryBatchResponse( id, itemId, itemName1, departmentId, departmentName1, batchNumber, currentQuantity, freeQuantity, purchaseRate, maximumRetailPrice, sellingRate, expiryDate, isExpired, isOutOfStock, taxRate1, supplierId1 );

        return inventoryBatchResponse;
    }
}
