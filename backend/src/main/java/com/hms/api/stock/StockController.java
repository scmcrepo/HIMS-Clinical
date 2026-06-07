package com.hms.api.stock;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.inventory.response.InventoryBatchResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.inventory.InventoryManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/stock") @RequiredArgsConstructor
@PreAuthorize("hasPermission('STOCK','')")
public class StockController {
    private final InventoryManagementService inventoryService;

    @GetMapping("/getStockByItemAndDept")
    public ResponseEntity<ApiResponse<List<InventoryBatchResponse>>> getByItemAndDept(
            @RequestParam(name = "itemId") UUID itemId, @RequestParam(name = "departmentId") UUID departmentId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", inventoryService.getAvailableBatches(itemId, departmentId)));
    }

    @GetMapping("/department/{deptId}")
    public ResponseEntity<ApiResponse<List<InventoryBatchResponse>>> getByDept(
            @PathVariable("deptId") UUID deptId, @RequestParam(name = "search", required=false) String search) {
        return ResponseEntity.ok(ApiResponse.ok("OK", inventoryService.getExpiredBatches(deptId)));
    }

    @GetMapping("/available/department/{deptId}")
    public ResponseEntity<ApiResponse<List<InventoryBatchResponse>>> getAvailableByDept(
            @PathVariable("deptId") UUID deptId, @RequestParam(name = "itemIds", required=false) List<UUID> itemIds) {
        return ResponseEntity.ok(ApiResponse.ok("OK", inventoryService.getExpiredBatches(deptId)));
    }
}
