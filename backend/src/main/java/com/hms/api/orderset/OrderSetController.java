package com.hms.api.orderset;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.orderset.model.OrderSet;
import com.hms.domain.orderset.model.OrderSetItem;
import com.hms.domain.shared.model.EntityStatus;
import com.hms.infrastructure.persistence.orderset.OrderSetJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * OrderSetController — CRUD for pre-configured drug/diagnostic order sets.
 *
 * GET    /order-sets                                   — search (optional ?q=)
 * GET    /order-sets/{id}                              — get by ID
 * GET    /order-sets/favorites?consultantId=            — consultant favorites
 * POST   /order-sets                                   — create
 * PUT    /order-sets/{id}                              — update
 * DELETE /order-sets/{id}                              — soft-delete
 * POST   /order-sets/{id}/favorite?consultantId=       — mark as favorite
 * DELETE /order-sets/{id}/favorite                     — unmark favorite
 */
@RestController
@RequestMapping("/order-sets")
@RequiredArgsConstructor
public class OrderSetController {

    private final OrderSetJpaRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderSet>>> search(
            @RequestParam(required = false) String q) {
        List<OrderSet> result = (q != null && !q.isBlank())
            ? repo.searchActive(q)
            : repo.findAllActive();
        return ResponseEntity.ok(ApiResponse.ok("OK", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderSet>> getById(@PathVariable UUID id) {
        return repo.findById(id)
            .map(o -> ResponseEntity.ok(ApiResponse.ok("OK", o)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/favorites")
    public ResponseEntity<ApiResponse<List<OrderSet>>> getFavorites(
            @RequestParam UUID consultantId) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            repo.findFavoritesByConsultant(consultantId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderSet>> create(@RequestBody OrderSet req) {
        if (req.getStatus() == null) req.setStatus(EntityStatus.ACTIVE);
        // Bi-directional link items → orderSet
        if (req.getItems() != null) {
            req.getItems().forEach(item -> item.setOrderSet(req));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Order set created", repo.save(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderSet>> update(
            @PathVariable UUID id,
            @RequestBody OrderSet req) {
        OrderSet existing = repo.findById(id)
            .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("OrderSet", id));
        existing.setName(req.getName());
        existing.setDescription(req.getDescription());
        existing.setSetType(req.getSetType());
        existing.setIsOutpatient(req.getIsOutpatient());
        existing.setScope(req.getScope());
        existing.setConsultantId(req.getConsultantId());
        existing.setDepartmentId(req.getDepartmentId());
        // Replace items
        existing.getItems().clear();
        if (req.getItems() != null) {
            req.getItems().forEach(item -> { item.setOrderSet(existing); existing.getItems().add(item); });
        }
        return ResponseEntity.ok(ApiResponse.ok("Order set updated", repo.save(existing)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        repo.findById(id).ifPresent(o -> { o.setStatus(EntityStatus.DELETED); repo.save(o); });
        return ResponseEntity.ok(ApiResponse.ok("Order set deleted", null));
    }

    /** Mark a global/shared order set as a favorite for a specific consultant */
    @PostMapping("/{id}/favorite")
    public ResponseEntity<ApiResponse<OrderSet>> markFavorite(
            @PathVariable UUID id,
            @RequestParam UUID consultantId) {
        // Create a consultant-specific copy marked as favorite
        OrderSet source = repo.findById(id)
            .orElseThrow(() -> new com.hms.exception.ResourceNotFoundException("OrderSet", id));

        OrderSet fav = new OrderSet();
        fav.setName(source.getName());
        fav.setDescription(source.getDescription());
        fav.setSetType(source.getSetType());
        fav.setIsOutpatient(source.getIsOutpatient());
        fav.setIsFavorite(true);
        fav.setConsultantId(consultantId);
        fav.setScope("CONSULTANT");
        fav.setStatus(EntityStatus.ACTIVE);

        source.getItems().forEach(item -> {
            OrderSetItem copy = new OrderSetItem();
            copy.setOrderSet(fav);
            copy.setItemType(item.getItemType());
            copy.setServiceCatalogItemId(item.getServiceCatalogItemId());
            copy.setItemName(item.getItemName());
            copy.setDiagnosticType(item.getDiagnosticType());
            copy.setQuantity(item.getQuantity());
            copy.setInstruction(item.getInstruction());
            copy.setFrequency(item.getFrequency());
            copy.setDuration(item.getDuration());
            copy.setRouteLabel(item.getRouteLabel());
            fav.getItems().add(copy);
        });
        return ResponseEntity.ok(ApiResponse.ok("Added to favorites", repo.save(fav)));
    }

    /** Create a single-item favorite directly (for drug/test quick-save from prescription tab) */
    @PostMapping("/favorites/item")
    public ResponseEntity<ApiResponse<OrderSet>> addFavoriteItem(
            @RequestBody Map<String, Object> payload) {
        String consultantId = str(payload.get("consultantId"));
        String itemType     = str(payload.getOrDefault("itemType", "PHARMACY"));
        String itemName     = str(payload.get("itemName"));
        String setType      = "PHARMACY".equals(itemType) ? "PRESCRIPTION" : "DIAGNOSTICS";

        OrderSet fav = new OrderSet();
        fav.setName("⭐ " + (itemName != null ? itemName : "Favorite"));
        fav.setSetType(setType);
        fav.setIsFavorite(true);
        fav.setIsOutpatient(true);
        fav.setScope("CONSULTANT");
        fav.setStatus(EntityStatus.ACTIVE);
        if (consultantId != null) {
            try { fav.setConsultantId(UUID.fromString(consultantId)); } catch (Exception ignored) {}
        }

        OrderSetItem item = new OrderSetItem();
        item.setOrderSet(fav);
        item.setItemType(itemType);
        item.setItemName(itemName);
        if (payload.get("serviceCatalogItemId") != null) {
            try { item.setServiceCatalogItemId(UUID.fromString(str(payload.get("serviceCatalogItemId")))); }
            catch (Exception ignored) {}
        }
        item.setQuantity(payload.get("quantity") instanceof Number n ? n.intValue() : 1);
        item.setFrequency(str(payload.get("frequency")));
        item.setDuration(str(payload.get("duration")));
        item.setInstruction(str(payload.get("instruction")));
        item.setRouteLabel(str(payload.get("routeLabel")));
        fav.getItems().add(item);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Favorite item saved", repo.save(fav)));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
