package com.hms.api.tempstock;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.inventory.model.TempStock;
import com.hms.infrastructure.persistence.stock.TempStockJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/tempStock") @RequiredArgsConstructor
public class TempStockController {
    private final TempStockJpaRepository repo;
    @PostMapping
    public ResponseEntity<ApiResponse<TempStock>> create(@RequestBody TempStock req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("OK", repo.save(req)));
    }
    @GetMapping("/detail/item/{itemId}")
    public ResponseEntity<ApiResponse<List<TempStock>>> getByItem(
            @PathVariable("itemId") UUID itemId, @RequestParam(name = "batch", required=false) String batch) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findByItemId(itemId)));
    }
    @GetMapping("/detail/item/{itemId}/quantity")
    public ResponseEntity<ApiResponse<Integer>> getQuantity(
            @PathVariable("itemId") UUID itemId, @RequestParam(name = "batch", required=false) String batch) {
        Integer qty = repo.sumQuantity(itemId, batch);
        return ResponseEntity.ok(ApiResponse.ok("OK", qty != null ? qty : 0));
    }
}
