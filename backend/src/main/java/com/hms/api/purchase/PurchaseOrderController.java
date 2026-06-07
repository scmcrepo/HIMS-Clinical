package com.hms.api.purchase;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.procurement.model.*;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.infrastructure.persistence.procurement.PurchaseOrderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping({"/purchase-orders", "/purchaseOrder"}) @RequiredArgsConstructor
@Transactional
@PreAuthorize("hasPermission('PURCHASE_ORDER','')")
public class PurchaseOrderController {
    private final PurchaseOrderJpaRepository orderRepo;
    private final SequenceNumberPort sequencePort;

    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrder>> create(@RequestBody PurchaseOrder req) {
        req.setOrderDate(LocalDate.now()); // Date always forced to today (legacy behaviour)
        req.setOrderStatus("ORDERED");
        if (req.getLines() != null) {
            for (PurchaseOrderLine line : req.getLines()) {
                line.setOrder(req);
            }
        }
        PurchaseOrder saved = orderRepo.save(req);
        saved.setSequenceNumber(sequencePort.generateNext(DocumentType.PURCHASE_ORDER));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Purchase order saved successfully", orderRepo.save(saved)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrder>> getById(@PathVariable("id") UUID id) {
        return orderRepo.findById(id)
            .map(o -> ResponseEntity.ok(ApiResponse.ok("OK", o)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseOrder>>> getByDate(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok("OK", orderRepo.findByOrderDate(date)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrder>> update(@PathVariable("id") UUID id, @RequestBody PurchaseOrder updateReq) {
        PurchaseOrder existing = orderRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Purchase order not found"));
        
        existing.setOrderStatus(updateReq.getOrderStatus());
        if (updateReq.getNotes() != null) existing.setNotes(updateReq.getNotes());
        
        if (updateReq.getLines() != null) {
            for (PurchaseOrderLine existingLine : existing.getLines()) {
                updateReq.getLines().stream()
                    .filter(l -> l.getId() != null && l.getId().equals(existingLine.getId()))
                    .findFirst()
                    .ifPresent(newLine -> {
                        existingLine.setReceivedQuantity(newLine.getReceivedQuantity());
                    });
            }
        }
        return ResponseEntity.ok(ApiResponse.ok("Purchase order updated successfully", orderRepo.save(existing)));
    }
}
