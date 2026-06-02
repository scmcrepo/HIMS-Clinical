package com.hms.api.stock;
import com.hms.api.inventory.response.InventoryBatchResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.inventory.InventoryManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping({"/stock-adjustment", "/stockAdjustment"}) @RequiredArgsConstructor
public class StockAdjustmentController {
    private final InventoryManagementService inventoryService;
    @PostMapping
    public ResponseEntity<ApiResponse<InventoryBatchResponse>> adjust(
            @RequestParam(name = "batchId") UUID batchId, @RequestParam(name = "adjustmentQty") int adjustmentQty, @RequestParam(name = "reason", required = false) String reason) {
        var result = inventoryService.adjustStock(new com.hms.api.inventory.request.AdjustStockRequest(batchId, adjustmentQty, reason));
        return ResponseEntity.ok(ApiResponse.ok("Stock adjusted successfully", result));
    }
}
