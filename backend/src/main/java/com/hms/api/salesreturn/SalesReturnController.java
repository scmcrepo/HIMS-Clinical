package com.hms.api.salesreturn;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.api.shared.ApiResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.inventory.model.InventoryBatch;
import com.hms.domain.sales.model.*;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import com.hms.infrastructure.persistence.sales.PharmacySaleJpaRepository;
import com.hms.infrastructure.persistence.salesreturn.SalesReturnJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * SalesReturnController — pharmacy sales return processing.
 *
 * Return quantity rule: totalReturnQty (existing + new) must not exceed original sale qty.
 * Stock restoration: batch currentQuantity incremented by return qty.
 * Sequence number: uses PHARMACY_SALE prefix (shares sequence — known legacy behaviour).
 */
@RestController
@RequestMapping({"/salesReturn"})
@RequiredArgsConstructor
@Transactional
@PreAuthorize("hasPermission('SALES_RETURN','')")
public class SalesReturnController {

    private final SalesReturnJpaRepository returnRepo;
    private final PharmacySaleJpaRepository saleRepo;
    private final InventoryBatchJpaRepository batchRepo;
    private final SequenceNumberPort sequencePort;

    @PostMapping
    public ResponseEntity<ApiResponse<SalesReturn>> create(@RequestBody Map<String, Object> body) {
        UUID saleId = UUID.fromString(body.get("saleId").toString());

        PharmacySale sale = saleRepo.findById(saleId)
            .orElseThrow(() -> new ResourceNotFoundException("PharmacySale", saleId));

        // Get all existing returns for this sale to compute already returned quantities
        List<SalesReturn> existingReturns = returnRepo.findBySaleId(saleId);

        SalesReturn ret = new SalesReturn();
        ret.setSaleId(saleId);
        ret.setPatientId(sale.getPatientId());
        ret.setDepartmentId(sale.getDepartmentId());
        ret.setReturnDate(LocalDate.now());

        List<Map<String, Object>> returnLines = (List<Map<String, Object>>) body.get("lines");
        for (var rl : returnLines) {
            UUID batchId = UUID.fromString(rl.get("inventoryBatchId").toString());
            int  qty     = Integer.parseInt(rl.get("quantity").toString());

            // Calculate already returned quantity for this batch in this sale
            int alreadyReturnedQty = existingReturns.stream()
                .flatMap(r -> r.getLines().stream())
                .filter(l -> l.getInventoryBatchId().equals(batchId))
                .mapToInt(SalesReturnLine::getQuantity).sum();

            // Validate: total return qty must not exceed sale qty
            int saleQty = sale.getLines().stream()
                .filter(l -> l.getInventoryBatchId().equals(batchId))
                .mapToInt(PharmacySaleLine::getQuantity).sum();
            if (alreadyReturnedQty + qty > saleQty) {
                throw new BusinessRuleViolationException(
                    "Quantity is greater than Purchased quantity");
            }

            // Restore stock
            InventoryBatch batch = batchRepo.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryBatch", batchId));
            batch.incrementStock(qty);
            batchRepo.save(batch);

            SalesReturnLine line = new SalesReturnLine();
            line.setInventoryBatchId(batchId);
            line.setQuantity(qty);
            var saleLine = sale.getLines().stream()
                .filter(l -> l.getInventoryBatchId().equals(batchId))
                .findFirst().orElseThrow();

            java.math.BigDecimal grossSaleAmount = sale.getLines().stream()
                .map(com.hms.domain.sales.model.PharmacySaleLine::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            java.math.BigDecimal discountRatio = java.math.BigDecimal.ONE;
            if (grossSaleAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                discountRatio = sale.getTotalAmount().divide(grossSaleAmount, 8, java.math.RoundingMode.HALF_UP);
            }

            java.math.BigDecimal grossReturnAmount = saleLine.getUnitRate().multiply(java.math.BigDecimal.valueOf(qty));
            java.math.BigDecimal netReturnAmount = grossReturnAmount.multiply(discountRatio).setScale(0, java.math.RoundingMode.HALF_UP);

            line.setReturnAmount(netReturnAmount);
            ret.addLine(line);
        }

        // Generate sequence number (uses PHARMACY_SALE prefix — legacy shared sequence)
        ret.setSequenceNumber(sequencePort.generateNext(DocumentType.PHARMACY_SALE));
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Sales return Saved successfully", returnRepo.save(ret)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SalesReturn>>> getByDate(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate queryDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.ok("OK", returnRepo.findByReturnDateStr(queryDate.toString())));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<List<SalesReturn>>> getByPatient(@PathVariable("patientId") UUID patientId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", returnRepo.findByPatientId(patientId)));
    }
}
