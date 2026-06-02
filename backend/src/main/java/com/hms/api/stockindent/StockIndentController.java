package com.hms.api.stockindent;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.inventory.model.StockIndent;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.stockmovement.StockIndentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping({"/stockIndent"}) @RequiredArgsConstructor
@Transactional
public class StockIndentController {
    private final StockIndentJpaRepository repo;
    private final SequenceNumberPort sequencePort;
    @PostMapping
    public ResponseEntity<ApiResponse<StockIndent>> create(@RequestBody StockIndent req) {
        if (req.getIndentFromDeptId().equals(req.getIndentToDeptId()))
            throw new BusinessRuleViolationException("Cannot Indent Stock b/w Same department");
        req.setIndentDate(LocalDate.now());
        req.setIndentStatus("INDENT");
        req.setSequenceNumber(sequencePort.generateNext(DocumentType.REPLENISHMENT));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Stock Indent created successfully", repo.save(req)));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockIndent>>> getByDate(
            @RequestParam(name = "searchFromDate", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate searchFromDate) {
        return ResponseEntity.ok(ApiResponse.ok("OK", searchFromDate != null ? repo.findByDate(searchFromDate) : repo.findAll()));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockIndent>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(id).orElseThrow()));
    }
}
