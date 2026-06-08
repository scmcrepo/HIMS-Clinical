package com.hms.api.stock;

import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.api.stock.request.StockAdjustmentRequest;
import com.hms.api.stock.response.StockAdjustmentResponse;
import com.hms.application.inventory.StockAdjustmentService;
import com.hms.application.inventory.InventoryManagementService;
import com.hms.api.inventory.response.InventoryBatchResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.UUID;

@RestController 
@RequestMapping({"/stock-adjustment", "/stockAdjustment"}) 
@RequiredArgsConstructor
@PreAuthorize("hasPermission('STOCK_ADJUSTMENT','')")
public class StockAdjustmentController {

    private final StockAdjustmentService stockAdjustmentService;
    private final InventoryManagementService inventoryService;

    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<StockAdjustmentResponse>> create(
            @Valid @RequestBody StockAdjustmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Stock adjustment completed successfully", stockAdjustmentService.createAdjustment(req)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InventoryBatchResponse>> adjust(
            @RequestParam(name = "batchId") UUID batchId, 
            @RequestParam(name = "adjustmentQty") int adjustmentQty, 
            @RequestParam(name = "reason", required = false) String reason) {
        var result = inventoryService.adjustStock(new com.hms.api.inventory.request.AdjustStockRequest(batchId, adjustmentQty, reason));
        return ResponseEntity.ok(ApiResponse.ok("Stock adjusted successfully", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockAdjustmentResponse>>> getAll(
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok("OK", stockAdjustmentService.searchAdjustments(q, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockAdjustmentResponse>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", stockAdjustmentService.getAdjustmentById(id)));
    }
}
