package com.hms.application.inventory;

import com.hms.api.stock.request.StockAdjustmentRequest;
import com.hms.api.stock.response.StockAdjustmentResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.inventory.model.InventoryBatch;
import com.hms.domain.inventory.model.StockAdjustment;
import com.hms.domain.inventory.model.StockAdjustmentLine;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.exception.ResourceNotFoundException;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryItemJpaRepository;
import com.hms.infrastructure.persistence.inventory.StockAdjustmentJpaRepository;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockAdjustmentService {

    private final StockAdjustmentJpaRepository stockAdjustmentRepo;
    private final InventoryBatchJpaRepository batchRepo;
    private final InventoryItemJpaRepository itemRepo;
    private final DepartmentJpaRepository departmentRepo;
    private final UserJpaRepository userRepo;
    private final SequenceNumberPort sequenceNumberPort;

    @Transactional
    public StockAdjustmentResponse createAdjustment(StockAdjustmentRequest req) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new BusinessRuleViolationException("Stock adjustment must contain at least one line item");
        }

        // Generate next formatted sequence number
        String seq = sequenceNumberPort.generateNext(DocumentType.STOCK_ADJUSTMENT);

        StockAdjustment sa = new StockAdjustment();
        sa.setDepartmentId(req.departmentId());
        sa.setNotes(req.notes());
        sa.setSequenceNumber(seq);
        sa.setAdjustmentDate(LocalDate.now());

        for (var line : req.lines()) {
            InventoryBatch batch = batchRepo.findByIdForUpdate(line.inventoryBatchId())
                    .orElseThrow(() -> new ResourceNotFoundException("InventoryBatch", line.inventoryBatchId()));

            if (line.adjustmentQty() <= 0) {
                throw new BusinessRuleViolationException("Adjustment quantity must be greater than zero");
            }

            String type = line.adjustmentType().toUpperCase();
            if (!type.equals("ADD") && !type.equals("SUBTRACT")) {
                throw new BusinessRuleViolationException("Adjustment type must be either ADD or SUBTRACT");
            }

            if (type.equals("SUBTRACT")) {
                if (batch.getCurrentQuantity() < line.adjustmentQty()) {
                    throw new BusinessRuleViolationException("Insufficient stock in batch " + batch.getBatchNumber() + 
                            ". Current: " + batch.getCurrentQuantity() + ", Required subtraction: " + line.adjustmentQty());
                }
                batch.decrementStock(line.adjustmentQty());
            } else {
                batch.incrementStock(line.adjustmentQty());
            }

            batchRepo.save(batch);

            StockAdjustmentLine sal = new StockAdjustmentLine();
            sal.setInventoryBatchId(batch.getId());
            sal.setAdjustmentQty(line.adjustmentQty());
            sal.setAdjustmentType(type);
            sal.setReason(line.reason());
            sa.addLine(sal);
        }

        StockAdjustment saved = stockAdjustmentRepo.save(sa);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StockAdjustmentResponse> getAllAdjustments() {
        return stockAdjustmentRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StockAdjustmentResponse> searchAdjustments(String q, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockAdjustment> pagedEntities;
        
        if (q != null && !q.trim().isEmpty()) {
            pagedEntities = stockAdjustmentRepo.findBySequenceNumberContainingIgnoreCaseOrderByCreatedAtDesc(q.trim(), pageable);
        } else {
            pagedEntities = stockAdjustmentRepo.findAllByOrderByCreatedAtDesc(pageable);
        }
        
        List<StockAdjustmentResponse> content = pagedEntities.getContent().stream()
                .map(this::mapToResponse)
                .toList();
                
        return new PageImpl<>(content, pageable, pagedEntities.getTotalElements());
    }

    @Transactional(readOnly = true)
    public StockAdjustmentResponse getAdjustmentById(UUID id) {
        StockAdjustment sa = stockAdjustmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockAdjustment", id));
        return mapToResponse(sa);
    }

    private StockAdjustmentResponse mapToResponse(StockAdjustment sa) {
        String deptName = departmentRepo.findById(sa.getDepartmentId())
                .map(com.hms.domain.shared.model.Department::getName)
                .orElse("Unknown Dept");

        String authorizer = "System";
        if (sa.getCreatedBy() != null) {
            authorizer = userRepo.findById(sa.getCreatedBy())
                    .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                    .map(name -> name.isEmpty() ? "Unknown User" : name)
                    .orElse("Unknown User");
        }

        List<StockAdjustmentResponse.LineResponse> lines = sa.getLines().stream().map(line -> {
            var batch = batchRepo.findById(line.getInventoryBatchId()).orElse(null);
            String batchNo = batch != null ? batch.getBatchNumber() : "Unknown Batch";
            String itemName = "Unknown Item";
            if (batch != null) {
                itemName = itemRepo.findById(batch.getItemId())
                        .map(com.hms.domain.inventory.model.InventoryItem::getName)
                        .orElse("Unknown Item");
            }
            return new StockAdjustmentResponse.LineResponse(
                line.getId(),
                line.getInventoryBatchId(),
                batchNo,
                itemName,
                line.getAdjustmentQty(),
                line.getAdjustmentType(),
                line.getReason()
            );
        }).toList();

        return new StockAdjustmentResponse(
            sa.getId(),
            sa.getDepartmentId(),
            deptName,
            sa.getSequenceNumber(),
            sa.getAdjustmentDate(),
            sa.getNotes(),
            authorizer,
            sa.getCreatedAt(),
            lines
        );
    }
}
