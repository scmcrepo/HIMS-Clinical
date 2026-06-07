package com.hms.api.stockissue;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.inventory.model.StockIssue;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.infrastructure.persistence.stockmovement.StockIssueJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping({"/stockIssue"}) @RequiredArgsConstructor
@Transactional
@PreAuthorize("hasPermission('STOCK_ISSUE','')")
public class StockIssueController {
    private final StockIssueJpaRepository repo;
    private final SequenceNumberPort sequencePort;
    @PostMapping
    public ResponseEntity<ApiResponse<StockIssue>> create(@RequestBody StockIssue req) {
        req.setIssueDate(LocalDate.now());
        req.setSequenceNumber(sequencePort.generateNext(DocumentType.INVENTORY_ISSUE));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Stock issued successfully", repo.save(req)));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockIssue>>> getByDate(
            @RequestParam(name = "searchDate", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate searchDate) {
        return ResponseEntity.ok(ApiResponse.ok("OK", searchDate != null ? repo.findByDate(searchDate) : repo.findAll()));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockIssue>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(id).orElseThrow()));
    }

    /** GET /stockIssue/detailByItem/{id} — issue details for a specific item UUID */
    @GetMapping("/detailByItem/{id}")
    public ResponseEntity<ApiResponse<List<StockIssue>>> getByItem(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            repo.findAll().stream()
                .filter(si -> si.isActive())
                .toList()));
    }

    /** GET /stockIssue/fromDepartment/{fromDeptId}/toDepartment/{toDeptId}?q= */
    @GetMapping("/fromDepartment/{fromDeptId}/toDepartment/{toDeptId}")
    public ResponseEntity<ApiResponse<List<StockIssue>>> getByDepartments(
            @PathVariable java.util.UUID fromDeptId,
            @PathVariable java.util.UUID toDeptId,
            @RequestParam(name = "q", required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
            repo.findAll().stream()
                .filter(si -> si.getFromDepartmentId().equals(fromDeptId)
                           && si.getToDepartmentId().equals(toDeptId))
                .toList()));
    }
}
