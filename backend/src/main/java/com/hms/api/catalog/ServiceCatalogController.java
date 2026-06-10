package com.hms.api.catalog;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.catalog.request.CreateServiceItemRequest;
import com.hms.api.catalog.request.UpdatePricingTierRequest;
import com.hms.api.catalog.response.ServiceCategoryResponse;
import com.hms.api.catalog.response.ServiceItemResponse;
import com.hms.api.shared.ApiResponse;
import com.hms.application.catalog.ServiceCatalogService;
import com.hms.domain.catalog.model.ServiceCategoryType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('SETTINGS_ITEM','')")
public class ServiceCatalogController {

    private final ServiceCatalogService catalogService;

    // ── Items ──────────────────────────────────────────────────────────────

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<ServiceItemResponse>> createItem(
            @Valid @RequestBody CreateServiceItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Service item created", catalogService.createServiceItem(req)));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<ServiceItemResponse>> getItem(@PathVariable("itemId") UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", catalogService.getById(itemId)));
    }

    @GetMapping("/items/search")
    public ResponseEntity<ApiResponse<Page<ServiceItemResponse>>> search(
            @RequestParam(name = "q", defaultValue = "") String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "excludeRoomCharges", defaultValue = "false") boolean excludeRoomCharges,
            @RequestParam(name = "diagnosticsAndConsultationsOnly", defaultValue = "false") boolean diagnosticsAndConsultationsOnly) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return ResponseEntity.ok(ApiResponse.ok("OK", catalogService.searchItems(q, excludeRoomCharges, diagnosticsAndConsultationsOnly, pageable)));
    }

    @GetMapping("/items/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ServiceItemResponse>>> getByCategory(
            @PathVariable("categoryId") UUID categoryId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", catalogService.getByCategory(categoryId)));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<ServiceItemResponse>> updateItem(
            @PathVariable("itemId") UUID itemId, @Valid @RequestBody CreateServiceItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Updated", catalogService.updateServiceItem(itemId, req)));
    }

    @PutMapping("/items/{itemId}/pricing")
    public ResponseEntity<ApiResponse<ServiceItemResponse>> updatePricing(
            @PathVariable("itemId") UUID itemId, @Valid @RequestBody UpdatePricingTierRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Pricing updated",
            catalogService.updatePricingTier(itemId, req)));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deactivateItem(@PathVariable("itemId") UUID itemId) {
        catalogService.deactivateServiceItem(itemId);
        return ResponseEntity.ok(ApiResponse.ok("Item deactivated"));
    }

    @PostMapping("/items/{itemId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateItem(@PathVariable("itemId") UUID itemId) {
        catalogService.activateServiceItem(itemId);
        return ResponseEntity.ok(ApiResponse.ok("Item activated"));
    }

    // ── Categories ─────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<ServiceCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok("OK", catalogService.getAllCategories()));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<ServiceCategoryResponse>> createCategory(
            @RequestParam(name = "name") String name,
            @RequestParam(name = "type") ServiceCategoryType type) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Category created", catalogService.createCategory(name, type)));
    }
}
