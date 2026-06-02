package com.hms.api.inventory;

import com.hms.api.inventory.request.AdjustStockRequest;
import com.hms.api.inventory.request.IssueStockRequest;
import com.hms.api.inventory.response.InventoryBatchResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.inventory.InventoryManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Handles in-warehouse stock operations.
 * Note: Receiving new stock is handled by GoodsReceivedController (/goods-received).
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryManagementService inventoryService;

    @PostMapping("/issue")
    public ResponseEntity<ApiResponse<Void>> issueStock(@Valid @RequestBody IssueStockRequest req) {
        inventoryService.issueStock(req);
        return ResponseEntity.ok(ApiResponse.ok("Stock issued successfully"));
    }

    @PatchMapping("/adjust")
    public ResponseEntity<ApiResponse<InventoryBatchResponse>> adjustStock(
            @Valid @RequestBody AdjustStockRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Stock adjusted",
            inventoryService.adjustStock(req)));
    }

    @PostMapping("/consume/{batchId}")
    public ResponseEntity<ApiResponse<InventoryBatchResponse>> consumeStock(
            @PathVariable("batchId") UUID batchId,
            @RequestParam("quantity") int quantity) {
        return ResponseEntity.ok(ApiResponse.ok("Stock consumed",
            inventoryService.consumeStock(batchId, quantity)));
    }

    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<List<InventoryBatchResponse>>> getAvailableBatches(
            @RequestParam("itemId") UUID itemId,
            @RequestParam("departmentId") UUID departmentId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            inventoryService.getAvailableBatches(itemId, departmentId)));
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<ApiResponse<InventoryBatchResponse>> getBatch(@PathVariable("batchId") UUID batchId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            inventoryService.getBatchById(batchId)));
    }

    @GetMapping("/expired")
    public ResponseEntity<ApiResponse<List<InventoryBatchResponse>>> getExpired(
            @RequestParam("departmentId") UUID departmentId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            inventoryService.getExpiredBatches(departmentId)));
    }

    /**
     * GET /inventory/batches/all — returns every batch in the system.
     * Used by the Opening Stock view page to display all bulk-imported records.
     */
    @GetMapping("/batches/all")
    public ResponseEntity<ApiResponse<List<InventoryBatchResponse>>> getAllBatches() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            inventoryService.getAllBatches()));
    }
}
