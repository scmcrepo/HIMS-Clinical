package com.hms.api.purchaserequest;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.procurement.model.PurchaseRequest;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.infrastructure.persistence.purchaserequest.PurchaseRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping({"/purchaseRequest"}) @RequiredArgsConstructor
@Transactional
@PreAuthorize("hasPermission('PURCHASE_REQUEST','')")
public class PurchaseRequestController {
    private final PurchaseRequestJpaRepository repo;
    private final SequenceNumberPort sequencePort;
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseRequest>> create(@RequestBody PurchaseRequest req) {
        req.setRequestDate(LocalDate.now());
        req.setRequestStatus("REQUESTED");
        req.setSequenceNumber(sequencePort.generateNext(DocumentType.PURCHASE_REQUEST));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Purchase request saved successfully", repo.save(req)));
    }
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseRequest>> update(@PathVariable("id") UUID id, @RequestBody PurchaseRequest req) {
        PurchaseRequest existing = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Purchase request not found"));
        existing.setRequestStatus(req.getRequestStatus());
        existing.setNotes(req.getNotes());
        return ResponseEntity.ok(ApiResponse.ok("Purchase request updated successfully", repo.save(existing)));
    }
    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseRequest>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findAllActive()));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseRequest>> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", repo.findById(id).orElseThrow()));
    }
    @GetMapping("/grnType")
    public ResponseEntity<ApiResponse<String[]>> getTypes() {
        return ResponseEntity.ok(ApiResponse.ok("OK", new String[]{"PURCHASE_REQUEST","DIRECT_PURCHASE"}));
    }
}
