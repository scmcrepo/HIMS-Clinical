package com.hms.api.stockreturn;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.inventory.model.StockReturn;
import com.hms.infrastructure.persistence.stockmovement.StockReturnJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping({"/stockReturn"}) @RequiredArgsConstructor
public class StockReturnController {
    private final StockReturnJpaRepository repo;
    @PostMapping
    public ResponseEntity<ApiResponse<StockReturn>> create(@RequestBody StockReturn req) {
        req.setReturnDate(LocalDate.now());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Stock return saved successfully", repo.save(req)));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockReturn>>> getByDate(
            @RequestParam(name = "date", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK", date != null ? repo.findByDate(date) : repo.findAll()));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockReturn>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(id).orElseThrow()));
    }
}
