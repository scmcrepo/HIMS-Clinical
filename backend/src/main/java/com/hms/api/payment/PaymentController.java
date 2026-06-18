package com.hms.api.payment;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.billing.model.PettyCash;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.billing.PettyCashJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
@PreAuthorize("hasPermission('PETTY_CASH','')")
@Transactional
public class PaymentController {
    private final PettyCashJpaRepository repo;
    private final SequenceNumberPort sequencePort;

    @PostMapping
    public ResponseEntity<ApiResponse<PettyCash>> create(@RequestBody PettyCash req) {
        if (req.getGivenTo() == null || req.getGivenTo().trim().length() < 3 || req.getGivenTo().trim().length() > 15) {
            throw new IllegalArgumentException("Given name must be between 3 and 15 characters");
        }
        if (req.getAmount() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        req.setPaymentDate(req.getPaymentDate() != null ? req.getPaymentDate() : LocalDate.now());
        req.setSequenceNumber(sequencePort.generateNext(DocumentType.PAYMENT));
        req.setStatus("Active");
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Payment information Saved successfully", repo.save(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PettyCash>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("PettyCash", id))));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PettyCash>>> getAll(
            @RequestParam(name = "dateSearch", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dateSearch,
            @RequestParam(name = "toDateSearch", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDateSearch,
            @RequestParam(name = "searchValue", required=false) String searchValue,
            @RequestParam(name = "start", defaultValue="0") int start,
            @RequestParam(name = "limit", defaultValue="20") int limit) {
        LocalDate from = (dateSearch != null) ? dateSearch : LocalDate.of(1970, 1, 1);
        LocalDate to = (toDateSearch != null) ? toDateSearch : LocalDate.of(9999, 12, 31);
        Page<PettyCash> page;
        if (searchValue == null || searchValue.trim().isEmpty()) {
            page = repo.findByFilters(from, to, PageRequest.of(start / limit, limit));
        } else {
            page = repo.findByFiltersWithSearch(from, to, searchValue.trim(), PageRequest.of(start / limit, limit));
        }
        return ResponseEntity.ok(ApiResponse.ok("OK", page.getContent()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable("id") UUID id) {
        PettyCash p = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("PettyCash", id));
        p.setStatus("Cancelled");
        repo.save(p);
        return ResponseEntity.ok(ApiResponse.ok("Payment information cancelled successfully"));
    }
}
