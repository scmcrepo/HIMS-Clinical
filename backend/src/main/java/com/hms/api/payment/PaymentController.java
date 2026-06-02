package com.hms.api.payment;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.*;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.billing.PaymentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/payment") @RequiredArgsConstructor
@PreAuthorize("hasPermission('PAYMENT','')")
@Transactional
public class PaymentController {
    private final PaymentJpaRepository repo;
    private final SequenceNumberPort sequencePort;
    @PostMapping
    public ResponseEntity<ApiResponse<Payment>> create(@RequestBody Payment req) {
        req.setPaymentDate(LocalDate.now());
        req.setSequenceNumber(sequencePort.generateNext(DocumentType.PAYMENT));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Payment information Saved successfully", repo.save(req)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Payment>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id))));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<Payment>>> getAll(
            @RequestParam(name = "dateSearch", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dateSearch,
            @RequestParam(name = "toDateSearch", required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDateSearch,
            @RequestParam(name = "start", defaultValue="0") int start,
            @RequestParam(name = "limit", defaultValue="20") int limit) {
        Page<Payment> page = repo.findByDateRange(dateSearch, toDateSearch, PageRequest.of(start/limit, limit));
        return ResponseEntity.ok(ApiResponse.ok("OK", page.getContent()));
    }
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable("id") UUID id) {
        Payment p = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        p.setStatus("Cancelled");
        repo.save(p);
        return ResponseEntity.ok(ApiResponse.ok("Payment information cancelled successfully"));
    }
}
