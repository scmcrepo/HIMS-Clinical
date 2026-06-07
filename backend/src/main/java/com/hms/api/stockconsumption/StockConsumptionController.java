package com.hms.api.stockconsumption;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.inventory.model.StockConsumption;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.infrastructure.persistence.stockmovement.StockConsumptionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping({"/stockConsumption"}) @RequiredArgsConstructor
@Transactional
@PreAuthorize("hasPermission('STOCK_CONSUMPTION','')")
public class StockConsumptionController {
    private final StockConsumptionJpaRepository repo;
    private final SequenceNumberPort sequencePort;
    @PostMapping
    public ResponseEntity<ApiResponse<StockConsumption>> create(@RequestBody StockConsumption req) {
        req.setConsumptionDate(LocalDate.now());
        req.setSequenceNumber(sequencePort.generateNext(DocumentType.CONSUMPTION));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Stock Consumed Successfully", repo.save(req)));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockConsumption>>> getByDate(
            @RequestParam(name = "searchDate", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate searchDate) {
        return ResponseEntity.ok(ApiResponse.ok("OK", searchDate != null ? repo.findByDate(searchDate) : repo.findAll()));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockConsumption>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(id).orElseThrow()));
    }
}
