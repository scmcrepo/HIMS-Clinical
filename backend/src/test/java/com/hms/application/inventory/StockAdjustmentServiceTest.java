package com.hms.application.inventory;

import com.hms.api.stock.request.StockAdjustmentRequest;
import com.hms.api.stock.response.StockAdjustmentResponse;
import com.hms.domain.billing.model.DocumentType;
import com.hms.domain.inventory.model.InventoryBatch;
import com.hms.domain.inventory.model.StockAdjustment;
import com.hms.domain.shared.port.out.SequenceNumberPort;
import com.hms.exception.BusinessRuleViolationException;
import com.hms.infrastructure.persistence.department.DepartmentJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryBatchJpaRepository;
import com.hms.infrastructure.persistence.inventory.InventoryItemJpaRepository;
import com.hms.infrastructure.persistence.inventory.StockAdjustmentJpaRepository;
import com.hms.infrastructure.persistence.shared.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockAdjustmentServiceTest {

    @Mock private StockAdjustmentJpaRepository stockAdjustmentRepo;
    @Mock private InventoryBatchJpaRepository batchRepo;
    @Mock private InventoryItemJpaRepository itemRepo;
    @Mock private DepartmentJpaRepository departmentRepo;
    @Mock private UserJpaRepository userRepo;
    @Mock private SequenceNumberPort sequenceNumberPort;

    @InjectMocks
    private StockAdjustmentService service;

    @Test
    void testCreateAdjustment_Success_ADD() {
        UUID deptId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        String seqNo = "ADJ-00001";

        StockAdjustmentRequest.LineRequest lineReq = new StockAdjustmentRequest.LineRequest(
                batchId, 10, "ADD", "Audit surplus"
        );
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                deptId, "Notes", List.of(lineReq)
        );

        InventoryBatch batch = new InventoryBatch();
        batch.setId(batchId);
        batch.setCurrentQuantity(50);
        batch.setBatchNumber("B123");

        when(sequenceNumberPort.generateNext(DocumentType.STOCK_ADJUSTMENT)).thenReturn(seqNo);
        when(batchRepo.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));
        when(stockAdjustmentRepo.save(any(StockAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockAdjustmentResponse response = service.createAdjustment(request);

        assertNotNull(response);
        assertEquals(seqNo, response.sequenceNumber());
        assertEquals(deptId, response.departmentId());
        assertEquals("Notes", response.notes());
        assertEquals(60, batch.getCurrentQuantity()); // 50 + 10 = 60
        verify(batchRepo).save(batch);
        verify(stockAdjustmentRepo).save(any(StockAdjustment.class));
    }

    @Test
    void testCreateAdjustment_Success_SUBTRACT() {
        UUID deptId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        String seqNo = "ADJ-00002";

        StockAdjustmentRequest.LineRequest lineReq = new StockAdjustmentRequest.LineRequest(
                batchId, 20, "SUBTRACT", "Spill/Loss"
        );
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                deptId, "Subtract Notes", List.of(lineReq)
        );

        InventoryBatch batch = new InventoryBatch();
        batch.setId(batchId);
        batch.setCurrentQuantity(50);
        batch.setBatchNumber("B123");

        when(sequenceNumberPort.generateNext(DocumentType.STOCK_ADJUSTMENT)).thenReturn(seqNo);
        when(batchRepo.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));
        when(stockAdjustmentRepo.save(any(StockAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockAdjustmentResponse response = service.createAdjustment(request);

        assertNotNull(response);
        assertEquals(30, batch.getCurrentQuantity()); // 50 - 20 = 30
        verify(batchRepo).save(batch);
    }

    @Test
    void testCreateAdjustment_Failure_InsufficientStock() {
        UUID deptId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        StockAdjustmentRequest.LineRequest lineReq = new StockAdjustmentRequest.LineRequest(
                batchId, 100, "SUBTRACT", "Spill/Loss"
        );
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                deptId, "Subtract Notes", List.of(lineReq)
        );

        InventoryBatch batch = new InventoryBatch();
        batch.setId(batchId);
        batch.setCurrentQuantity(50);
        batch.setBatchNumber("B123");

        when(sequenceNumberPort.generateNext(DocumentType.STOCK_ADJUSTMENT)).thenReturn("ADJ-00003");
        when(batchRepo.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));

        assertThrows(BusinessRuleViolationException.class, () -> service.createAdjustment(request));
        assertEquals(50, batch.getCurrentQuantity()); // Quantity remains unchanged
        verify(batchRepo, never()).save(any(InventoryBatch.class));
    }

    @Test
    void testCreateAdjustment_Failure_EmptyLines() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                UUID.randomUUID(), "No lines", Collections.emptyList()
        );
        assertThrows(BusinessRuleViolationException.class, () -> service.createAdjustment(request));
    }

    @Test
    void testCreateAdjustment_Failure_InvalidQuantity() {
        UUID deptId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();

        StockAdjustmentRequest.LineRequest lineReq = new StockAdjustmentRequest.LineRequest(
                batchId, 0, "ADD", "Audit surplus"
        );
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                deptId, "Notes", List.of(lineReq)
        );

        InventoryBatch batch = new InventoryBatch();
        batch.setId(batchId);
        batch.setCurrentQuantity(50);

        when(sequenceNumberPort.generateNext(DocumentType.STOCK_ADJUSTMENT)).thenReturn("ADJ-00004");
        when(batchRepo.findByIdForUpdate(batchId)).thenReturn(Optional.of(batch));

        assertThrows(BusinessRuleViolationException.class, () -> service.createAdjustment(request));
    }
}
