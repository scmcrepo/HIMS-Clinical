package com.hms.api.tempstock;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.inventory.model.TempStock;
import com.hms.domain.inventory.model.InventoryBatch;
import com.hms.infrastructure.persistence.stock.TempStockJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController @RequestMapping("/tempStock") @RequiredArgsConstructor
@PreAuthorize("hasPermission('STOCK','') or hasPermission('SALES','')")
public class TempStockController {
    private final TempStockJpaRepository repo;
    private final InventoryBatchJpaRepository batchRepo;

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<List<TempStock>>> createBulk(@RequestBody List<TempStock> reqs) {
        List<TempStock> savedList = new ArrayList<>();
        for (TempStock ts : reqs) {
            TempStock saved = repo.save(ts);
            savedList.add(saved);

            List<InventoryBatch> existing = batchRepo.findByItemDeptAndBatch(ts.getItemId(), ts.getDepartmentId(), ts.getBatchNumber());
            if (!existing.isEmpty()) {
                InventoryBatch batch = existing.get(0);
                batch.setCurrentQuantity(batch.getCurrentQuantity() + ts.getQuantity());
                batch.setPurchaseRate(ts.getPurchaseRate());
                batch.setMaximumRetailPrice(ts.getMrp());
                batch.setSellingRate(ts.getSellingRate());
                if (ts.getExpiryDate() != null) {
                    batch.setExpiryDate(ts.getExpiryDate());
                }
                batchRepo.save(batch);
            } else {
                InventoryBatch batch = new InventoryBatch();
                batch.setItemId(ts.getItemId());
                batch.setDepartmentId(ts.getDepartmentId());
                batch.setBatchNumber(ts.getBatchNumber());
                batch.setCurrentQuantity(ts.getQuantity());
                batch.setPurchaseRate(ts.getPurchaseRate());
                batch.setMaximumRetailPrice(ts.getMrp());
                batch.setSellingRate(ts.getSellingRate());
                batch.setExpiryDate(ts.getExpiryDate());
                batch.setSourceTransactionId(saved.getId());
                batchRepo.save(batch);
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("OK", savedList));
    }

    @GetMapping("/detail/item/{itemId}")
    public ResponseEntity<ApiResponse<List<TempStock>>> getByItem(
            @PathVariable("itemId") UUID itemId, @RequestParam(name = "batch", required=false) String batch) {
        List<TempStock> list = repo.findByItemId(itemId);
        Map<String, TempStock> activeMap = new LinkedHashMap<>();
        for (TempStock ts : list) {
            String key = ts.getDepartmentId() + "_" + ts.getBatchNumber();
            if (activeMap.containsKey(key)) {
                TempStock existingTs = activeMap.get(key);
                existingTs.setQuantity(existingTs.getQuantity() + ts.getQuantity());
            } else {
                TempStock copy = new TempStock();
                copy.setId(ts.getId());
                copy.setItemId(ts.getItemId());
                copy.setDepartmentId(ts.getDepartmentId());
                copy.setBatchNumber(ts.getBatchNumber());
                copy.setQuantity(ts.getQuantity());
                copy.setPurchaseRate(ts.getPurchaseRate());
                copy.setMrp(ts.getMrp());
                copy.setSellingRate(ts.getSellingRate());
                copy.setExpiryDate(ts.getExpiryDate());
                copy.setTaxRate(ts.getTaxRate());
                copy.setCreatedAt(ts.getCreatedAt());
                activeMap.put(key, copy);
            }
        }
        
        List<TempStock> result = new ArrayList<>();
        for (TempStock ts : activeMap.values()) {
            if (ts.getQuantity() > 0) {
                List<InventoryBatch> existing = batchRepo.findByItemDeptAndBatch(ts.getItemId(), ts.getDepartmentId(), ts.getBatchNumber());
                if (!existing.isEmpty()) {
                    ts.setQuantity(existing.get(0).getCurrentQuantity());
                } else {
                    ts.setQuantity(0);
                }
                if (ts.getQuantity() > 0) {
                    if (batch == null || batch.equalsIgnoreCase(ts.getBatchNumber())) {
                        result.add(ts);
                    }
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.ok("OK", result));
    }

    @GetMapping("/detail/item/{itemId}/quantity")
    public ResponseEntity<ApiResponse<Integer>> getQuantity(
            @PathVariable("itemId") UUID itemId, @RequestParam(name = "batch", required=false) String batch) {
        List<TempStock> list = repo.findByItemId(itemId);
        Map<String, Integer> sums = new HashMap<>();
        for (TempStock ts : list) {
            String key = ts.getDepartmentId() + "_" + ts.getBatchNumber();
            sums.put(key, sums.getOrDefault(key, 0) + ts.getQuantity());
        }
        
        int total = 0;
        for (Map.Entry<String, Integer> entry : sums.entrySet()) {
            if (entry.getValue() > 0) {
                String[] parts = entry.getKey().split("_", 2);
                UUID deptId = UUID.fromString(parts[0]);
                String batchNo = parts[1];
                if (batch == null || batch.equalsIgnoreCase(batchNo)) {
                    List<InventoryBatch> existing = batchRepo.findByItemDeptAndBatch(itemId, deptId, batchNo);
                    if (!existing.isEmpty()) {
                        total += existing.get(0).getCurrentQuantity();
                    }
                }
            }
        }
        return ResponseEntity.ok(ApiResponse.ok("OK", total));
    }
}
