package com.hms.application.goods;
import com.hms.api.goods.request.ReceiveGoodsRequest;
import com.hms.api.goods.response.PurchaseReceiptResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.inventory.model.InventoryBatch;
import com.hms.domain.procurement.model.*;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryItemJpaRepository;
import com.hms.infrastructure.persistence.procurement.PurchaseReceiptJpaRepository;
import com.hms.infrastructure.persistence.stock.TempStockJpaRepository;
import com.hms.domain.inventory.model.TempStock;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class GoodsReceivedService {
    private final PurchaseReceiptJpaRepository receiptRepo;
    private final InventoryBatchJpaRepository batchRepo;
    private final InventoryItemJpaRepository itemRepo;
    private final SequenceNumberPort sequencePort;
    private final TempStockJpaRepository tempStockRepo;

    @Transactional
    public PurchaseReceiptResponse receiveGoods(ReceiveGoodsRequest req) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        if (req.supplierId() == null)
            throw new BusinessRuleViolationException("Supplier is mandatory");
        receipt.setSupplierId(req.supplierId());
        receipt.setPurchaseOrderId(req.purchaseOrderId());
        receipt.setDepartmentId(req.departmentId());
        receipt.setReceiptDate(LocalDate.now());
        receipt.setInvoiceNumber(req.invoiceNumber());
        receipt.setInvoiceDate(req.invoiceDate());
        if (req.notes() == null || req.notes().trim().isEmpty())
            throw new BusinessRuleViolationException("Invoice type is mandatory");
        receipt.setNotes(req.notes());

        for (var line : req.lines()) {
            if (!itemRepo.existsById(line.itemId()))
                throw new ResourceNotFoundException("InventoryItem", line.itemId());
            if (line.batchNumber() == null || line.batchNumber().trim().isEmpty())
                throw new BusinessRuleViolationException("Batch number is mandatory for all items");
            if (line.expiryDate() == null)
                throw new BusinessRuleViolationException("Expiry date is mandatory for all items");
            if (line.expiryDate() != null && line.expiryDate().isBefore(LocalDate.now()))
                throw new BusinessRuleViolationException("Expiry date cannot be in the past for batch " + line.batchNumber());
            if (line.maximumRetailPrice() == null || line.maximumRetailPrice().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessRuleViolationException("MRP must be greater than zero for all items");
            if (line.purchaseRate() == null || line.purchaseRate().compareTo(BigDecimal.ZERO) <= 0)
                throw new BusinessRuleViolationException("Purchase rate must be greater than zero for all items");
            if (line.quantity() <= 0)
                throw new BusinessRuleViolationException("Quantity must be > 0 for item " + line.itemId());

            PurchaseReceiptLine rl = new PurchaseReceiptLine();
            rl.setItemId(line.itemId()); rl.setBatchNumber(line.batchNumber());
            rl.setQuantity(line.quantity()); rl.setPurchaseRate(line.purchaseRate());
            rl.setMaximumRetailPrice(line.maximumRetailPrice()); rl.setSellingRate(line.sellingRate());
            rl.setExpiryDate(line.expiryDate());
            receipt.addLine(rl);
        }

        PurchaseReceipt saved = receiptRepo.save(receipt);
        // Generate sequence number
        saved.setSequenceNumber(sequencePort.generateNext(DocumentType.PURCHASE_RECEIPT));
        saved = receiptRepo.save(saved);

        // Create inventory batches for each received line
        final UUID sourceId = saved.getId();
        for (var line : saved.getLines()) {
            int free = req.lines().stream()
                .filter(l -> l.itemId().equals(line.getItemId()) && Objects.equals(l.batchNumber(), line.getBatchNumber()))
                .mapToInt(l -> l.freeQty() != null ? l.freeQty() : 0)
                .findFirst()
                .orElse(0);

            int tempQty = req.lines().stream()
                .filter(l -> l.itemId().equals(line.getItemId()) && Objects.equals(l.batchNumber(), line.getBatchNumber()))
                .mapToInt(l -> l.tempQuantity() != null ? l.tempQuantity() : 0)
                .findFirst()
                .orElse(0);

            int actualAdjustment = 0;
            if (tempQty > 0) {
                Integer dbTempSum = tempStockRepo.sumQuantity(line.getItemId(), line.getBatchNumber());
                int tempSum = dbTempSum != null ? dbTempSum : 0;

                if (tempSum > 0) {
                    // Cap adjustment at what is actually remaining in the batch.
                    // This handles cases where items were already sold from temp stock:
                    // e.g. temp=100, sold=95 → batch has 5 → only subtract 5, not 100.
                    List<InventoryBatch> priorBatches = batchRepo.findByItemDeptAndBatch(
                        line.getItemId(), saved.getDepartmentId(), line.getBatchNumber());
                    int currentBatchQty = priorBatches.isEmpty() ? 0 : priorBatches.get(0).getCurrentQuantity();
                    actualAdjustment = Math.min(tempSum, currentBatchQty);

                    // Write a negative entry to zero out ALL remaining temp entries,
                    // preventing future double-adjustment
                    TempStock adjust = new TempStock();
                    adjust.setItemId(line.getItemId());
                    adjust.setDepartmentId(saved.getDepartmentId());
                    adjust.setBatchNumber(line.getBatchNumber());
                    adjust.setQuantity(-tempSum);
                    adjust.setPurchaseRate(line.getPurchaseRate());
                    adjust.setMrp(line.getMaximumRetailPrice());
                    adjust.setSellingRate(line.getSellingRate());
                    adjust.setExpiryDate(line.getExpiryDate());
                    adjust.setSourceReceiptId(saved.getId());
                    tempStockRepo.save(adjust);
                }
            }

            int netAdd = (line.getQuantity() + free) - actualAdjustment;

            List<InventoryBatch> existing = batchRepo.findByItemDeptAndBatch(line.getItemId(), saved.getDepartmentId(), line.getBatchNumber());
            if (!existing.isEmpty()) {
                InventoryBatch batch = existing.get(0);
                batch.setCurrentQuantity(batch.getCurrentQuantity() + netAdd);
                batch.setPurchaseRate(line.getPurchaseRate());
                batch.setMaximumRetailPrice(line.getMaximumRetailPrice());
                batch.setSellingRate(line.getSellingRate());
                if (line.getExpiryDate() != null) {
                    batch.setExpiryDate(line.getExpiryDate());
                }
                batchRepo.save(batch);
            } else {
                InventoryBatch batch = new InventoryBatch();
                batch.setItemId(line.getItemId());
                batch.setDepartmentId(saved.getDepartmentId());
                batch.setBatchNumber(line.getBatchNumber());
                batch.setCurrentQuantity(netAdd);
                batch.setFreeQuantity(free);
                batch.setPurchaseRate(line.getPurchaseRate());
                batch.setMaximumRetailPrice(line.getMaximumRetailPrice());
                batch.setSellingRate(line.getSellingRate());
                batch.setExpiryDate(line.getExpiryDate());
                batch.setSourceTransactionId(sourceId);
                batchRepo.save(batch);
            }
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PurchaseReceiptResponse> getByDate(LocalDate date) {
        return receiptRepo.findByReceiptDate(date).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptResponse getById(UUID id) {
        return toResponse(receiptRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("PurchaseReceipt", id)));
    }

    private PurchaseReceiptResponse toResponse(PurchaseReceipt r) {
        var lineResponses = r.getLines().stream()
            .map(l -> new PurchaseReceiptResponse.LineResponse(l.getId(), l.getItemId(), l.getBatchNumber(),
                l.getQuantity(), l.getPurchaseRate(), l.getMaximumRetailPrice(), l.getSellingRate(), l.getExpiryDate()))
            .collect(Collectors.toList());
        return new PurchaseReceiptResponse(r.getId(), r.getSupplierId(), r.getPurchaseOrderId(), r.getDepartmentId(),
            r.getReceiptDate(), r.getInvoiceNumber(), r.getInvoiceDate(), r.getNotes(), r.getSequenceNumber(), lineResponses);
    }
}
