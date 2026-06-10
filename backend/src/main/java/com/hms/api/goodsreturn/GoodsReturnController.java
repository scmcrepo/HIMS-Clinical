package com.hms.api.goodsreturn;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.inventory.model.InventoryBatch;
import com.hms.domain.procurement.model.GoodsReturn;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.goodsreturn.GoodsReturnJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import com.hms.infrastructure.persistence.procurement.PurchaseReceiptJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/goodsReturn") @RequiredArgsConstructor
@Transactional
@PreAuthorize("hasPermission('INVENTORY_GOODS_RETURN','')")
public class GoodsReturnController {
    private final GoodsReturnJpaRepository returnRepo;
    private final InventoryBatchJpaRepository batchRepo;
    private final PurchaseReceiptJpaRepository receiptRepo;
    private final SequenceNumberPort sequencePort;

    @PostMapping
    public ResponseEntity<ApiResponse<GoodsReturn>> create(@RequestBody Map<String, Object> body) {
        GoodsReturn ret = new GoodsReturn();
        if (body.get("supplierId") != null) ret.setSupplierId(UUID.fromString(body.get("supplierId").toString()));
        ret.setDepartmentId(UUID.fromString(body.get("departmentId").toString()));
        ret.setReturnDate(LocalDate.now());
        ret.setNotes(body.getOrDefault("notes", "").toString());

        // Validate and reduce stock for each return line
        List<Map<String, Object>> lines = (List<Map<String, Object>>) body.get("lines");
        if (lines != null) {
            for (var line : lines) {
                UUID batchId = UUID.fromString(line.get("batchId").toString());
                int qty = Integer.parseInt(line.get("quantity").toString());
                InventoryBatch batch = batchRepo.findByIdForUpdate(batchId)
                    .orElseThrow(() -> new BusinessRuleViolationException("Batch not found: " + batchId));
                
                // Calculate free quantity returned
                int originalFreeQty = batch.getFreeQuantity();
                int originalChargedQty = 0;
                if (batch.getSourceTransactionId() != null) {
                    var receipt = receiptRepo.findById(batch.getSourceTransactionId()).orElse(null);
                    if (receipt != null) {
                        originalChargedQty = receipt.getLines().stream()
                            .filter(l -> l.getItemId().equals(batch.getItemId()) && Objects.equals(l.getBatchNumber(), batch.getBatchNumber()))
                            .mapToInt(com.hms.domain.procurement.model.PurchaseReceiptLine::getQuantity)
                            .sum();
                    }
                }
                if (originalChargedQty <= 0) {
                    originalChargedQty = Math.max(0, batch.getCurrentQuantity() - originalFreeQty);
                }
                int totalPreviouslyReturnedCharged = returnRepo.findTotalChargedReturnedForBatch(batchId);
                int remainingCharged = Math.max(0, originalChargedQty - totalPreviouslyReturnedCharged);
                int chargedQtyReturned = Math.min(qty, remainingCharged);
                int freeQtyReturned = qty - chargedQtyReturned;

                // Add line to return record
                com.hms.domain.procurement.model.GoodsReturnLine grLine = new com.hms.domain.procurement.model.GoodsReturnLine();
                grLine.setBatchId(batchId);
                grLine.setQuantity(qty);
                grLine.setFreeQuantity(freeQtyReturned);
                grLine.setPurchaseRate(batch.getPurchaseRate());
                ret.addLine(grLine);

                // Reduce stock
                batch.decrementStock(qty);
                batchRepo.save(batch);
            }
        }
        ret.setSequenceNumber(sequencePort.generateNext(DocumentType.PURCHASE_RETURN));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Goods return saved successfully", returnRepo.save(ret)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoodsReturn>>> getByDate(
            @RequestParam(name = "date", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("OK", returnRepo.findByDate(d)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GoodsReturn>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", returnRepo.findById(id).orElseThrow()));
    }
}
