package com.hms.application.inventory;

import com.hms.api.inventory.request.AdjustStockRequest;
import com.hms.api.inventory.request.IssueStockRequest;
import com.hms.api.inventory.response.InventoryBatchResponse;
import com.hms.domain.inventory.model.InventoryBatch;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.mapper.InventoryMapper;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryItemJpaRepository;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Core inventory stock operations: issue, adjust, consume, query.
 * Receiving new stock (goods-in) is handled by GoodsReceivedService.
 */
@Service
@RequiredArgsConstructor
public class InventoryManagementService {

    private final InventoryBatchJpaRepository batchRepo;
    private final InventoryItemJpaRepository itemRepo;
    private final DepartmentJpaRepository departmentRepo;
    private final InventoryMapper inventoryMapper;
    private final com.hms.infrastructure.persistence.procurement.PurchaseReceiptJpaRepository receiptRepo;

    /**
     * Inter-department stock transfer.
     * Decrements source batch and creates a new batch in the target department.
     * PESSIMISTIC_WRITE on the source batch prevents concurrent issues.
     */
    @Transactional
    public void issueStock(IssueStockRequest req) {
        for (var line : req.lines()) {
            InventoryBatch sourceBatch = batchRepo.findByIdForUpdate(line.batchId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryBatch", line.batchId()));

            if (sourceBatch.isExpired()) {
                throw new BusinessRuleViolationException(
                    "Cannot issue expired batch: " + sourceBatch.getBatchNumber());
            }

            sourceBatch.decrementStock(line.quantity());
            batchRepo.save(sourceBatch);

            // Create receiving batch in target department
            InventoryBatch target = new InventoryBatch();
            target.setItemId(sourceBatch.getItemId());
            target.setDepartmentId(req.targetDepartmentId());
            target.setBatchNumber(sourceBatch.getBatchNumber());
            target.setCurrentQuantity(line.quantity());
            target.setPurchaseRate(sourceBatch.getPurchaseRate());
            target.setMaximumRetailPrice(sourceBatch.getMaximumRetailPrice());
            target.setSellingRate(sourceBatch.getSellingRate());
            target.setExpiryDate(sourceBatch.getExpiryDate());
            target.setSourceTransactionId(sourceBatch.getId());
            batchRepo.save(target);
        }
    }

    @Transactional
    public InventoryBatchResponse adjustStock(AdjustStockRequest req) {
        InventoryBatch batch = batchRepo.findByIdForUpdate(req.batchId())
            .orElseThrow(() -> new ResourceNotFoundException("InventoryBatch", req.batchId()));

        if (req.adjustmentQty() == 0)
            throw new BusinessRuleViolationException("Adjustment quantity cannot be zero");

        if (req.adjustmentQty() > 0) batch.incrementStock(req.adjustmentQty());
        else batch.decrementStock(Math.abs(req.adjustmentQty()));

        InventoryBatch saved = batchRepo.save(batch);
        return mapToResponse(saved);
    }

    @Transactional
    public InventoryBatchResponse consumeStock(UUID batchId, int quantity) {
        InventoryBatch batch = batchRepo.findByIdForUpdate(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("InventoryBatch", batchId));

        if (batch.isExpired())
            throw new BusinessRuleViolationException("Cannot consume from expired batch: " + batch.getBatchNumber());

        batch.decrementStock(quantity);
        InventoryBatch saved = batchRepo.save(batch);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InventoryBatchResponse> getAvailableBatches(UUID itemId, UUID departmentId) {
        return batchRepo.findAvailableByItemAndDept(itemId, departmentId).stream()
            .map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryBatchResponse> getExpiredBatches(UUID departmentId) {
        return batchRepo.findExpiredBatchesInDept(departmentId, LocalDate.now()).stream()
            .map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryBatchResponse getBatchById(UUID batchId) {
        InventoryBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryBatch", batchId));
        return mapToResponse(batch);
    }

    /**
     * Returns all inventory batches (opening stock view).
     * Supports optional search by item name / batch number and pagination.
     */
    @Transactional(readOnly = true)
    public List<InventoryBatchResponse> getAllBatches() {
        return batchRepo.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
            .stream()
            .map(this::mapToResponse)
            .toList();
    }

    private InventoryBatchResponse mapToResponse(InventoryBatch batch) {
        var item = itemRepo.findById(batch.getItemId()).orElse(null);
        String itemName = item != null ? item.getName() : "Unknown Item";
        java.math.BigDecimal taxRate = item != null ? item.getTaxRate() : java.math.BigDecimal.ZERO;
        String deptName = departmentRepo.findById(batch.getDepartmentId())
            .map(com.hms.domain.shared.model.Department::getName)
            .orElse("Unknown Dept");
        UUID supplierId = null;
        if (batch.getSourceTransactionId() != null) {
            supplierId = receiptRepo.findById(batch.getSourceTransactionId())
                .map(com.hms.domain.procurement.model.PurchaseReceipt::getSupplierId)
                .orElse(null);
        }
        return inventoryMapper.toResponse(batch, itemName, deptName, taxRate, supplierId);
    }
}
