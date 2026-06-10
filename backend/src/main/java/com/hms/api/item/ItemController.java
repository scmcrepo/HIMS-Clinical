package com.hms.api.item;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.inventory.model.InventoryItem;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.item.ItemJpaRepository;
import com.hms.infrastructure.persistence.scheduleddrug.ScheduledDrugJpaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ItemController — inventory item / medicine master.
 * Consumed by SalesService (item search), GoodsReceivedService, StockIssueService.
 */
@RestController
@RequestMapping("/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemJpaRepository itemRepo;
    private final ScheduledDrugJpaRepository scheduledDrugRepo;

    /** GET /item/getItemByName/department/{departmentId}?name= */
    @GetMapping("/getItemByName/department/{departmentId}")
    @PreAuthorize("hasPermission('SETTINGS_ITEM','')")
    public ResponseEntity<ApiResponse<List<InventoryItem>>> getByNameInDept(
            @PathVariable("departmentId") UUID departmentId,
            @RequestParam(name = "name", defaultValue = "") String name) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            itemRepo.searchByNameInDepartment(name, departmentId)));
    }

    /** GET /item/getItemByName?name= */
    @GetMapping("/getItemByName")
    @PreAuthorize("hasPermission('SETTINGS_ITEM','')")
    public ResponseEntity<ApiResponse<List<InventoryItem>>> getByName(
            @RequestParam(name = "name", defaultValue = "") String name) {
        return ResponseEntity.ok(ApiResponse.ok("OK", itemRepo.searchByName(name)));
    }

    /** POST /item */
    @PostMapping
    @PreAuthorize("hasPermission('SETTINGS_ITEM','')")
    public ResponseEntity<ApiResponse<InventoryItem>> create(@Valid @RequestBody InventoryItem req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Item saved successfully", itemRepo.save(req)));
    }

    /** PUT /item */
    @PutMapping
    @PreAuthorize("hasPermission('SETTINGS_ITEM','')")
    public ResponseEntity<ApiResponse<InventoryItem>> update(@Valid @RequestBody InventoryItem req) {
        if (req.getId() == null) return (ResponseEntity) ResponseEntity.badRequest().body(ApiResponse.error("id required"));
        return ResponseEntity.ok(ApiResponse.ok("Item updated successfully", itemRepo.save(req)));
    }

    /** GET /item/getItemById/{id} */
    @GetMapping("/getItemById/{id}")
    @PreAuthorize("hasPermission('SETTINGS_ITEM','')")
    public ResponseEntity<ApiResponse<InventoryItem>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            itemRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Item", id))));
    }

    /** GET /item?start=&limit=&value=&status=&id= — paginated with search */
    @GetMapping
    @PreAuthorize("hasPermission('SETTINGS_ITEM','')")
    public ResponseEntity<ApiResponse<Page<InventoryItem>>> getAll(
            @RequestParam(name = "start", defaultValue = "0")  int start,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "value", required = false)    String value,
            @RequestParam(name = "status", required = false)    String status,
            @RequestParam(required = false)    UUID id) {
        Pageable pageable = PageRequest.of(start / Math.max(limit, 1), Math.max(limit, 1));
        Page<InventoryItem> page = itemRepo.searchPagedWithCategory(value, id, pageable);
        return ResponseEntity.ok(ApiResponse.ok("OK", page));
    }

    /** GET /item/unitTypes — UnitType enum for UI dropdowns */
    @GetMapping("/unitTypes")
    public ResponseEntity<ApiResponse<String[]>> getUnitTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            new String[]{"Tablet", "Bottle", "Strip", "Box", "NOS", "Capsule", "ml", "mg", "Gram",
                         "Vial", "Ampoule", "Sachet", "Unit", "Piece"}));
    }

    /** GET /item/scheduledDrugType */
    @GetMapping("/scheduledDrugType")
    public ResponseEntity<ApiResponse<String[]>> getScheduledDrugTypes() {
        String[] types = scheduledDrugRepo.findAllActive().stream()
            .map(com.hms.domain.shared.model.ScheduledDrug::getName)
            .toArray(String[]::new);
        return ResponseEntity.ok(ApiResponse.ok("OK", types));
    }

    /** GET /item/getItemForPresctription?name= — prescription item search */
    @GetMapping("/getItemForPresctription")
    @PreAuthorize("hasPermission('SETTINGS_ITEM','')")
    public ResponseEntity<ApiResponse<List<InventoryItem>>> getForPrescription(
            @RequestParam(name = "name", defaultValue = "") String name) {
        return ResponseEntity.ok(ApiResponse.ok("OK", itemRepo.searchByName(name)));
    }
}
