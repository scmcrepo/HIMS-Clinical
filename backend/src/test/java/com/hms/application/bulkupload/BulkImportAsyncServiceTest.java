package com.hms.application.bulkupload;

import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkImportAsyncServiceTest {

    @Mock
    private BulkImportService bulkImportService;

    @InjectMocks
    private BulkImportAsyncService bulkImportAsyncService;

    private MockedStatic<TenantContext> mockedTenantContext;
    private MockedStatic<BranchContext> mockedBranchContext;
    private UUID jobId;
    private UUID tenantId;
    private UUID branchId;
    private List<Map<String, String>> rows;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        rows = Collections.emptyList();

        mockedTenantContext = mockStatic(TenantContext.class);
        mockedBranchContext = mockStatic(BranchContext.class);
    }

    @AfterEach
    void tearDown() {
        mockedTenantContext.close();
        mockedBranchContext.close();
    }

    @Test
    void processImportAsync_ShouldProcessSuccessfully() {
        bulkImportAsyncService.processImportAsync(jobId, "PATIENT", rows, tenantId, branchId);

        mockedTenantContext.verify(() -> TenantContext.set(tenantId));
        mockedBranchContext.verify(() -> BranchContext.set(branchId));
        verify(bulkImportService).processRowsAndSaveStatus(jobId, "PATIENT", rows);
        mockedTenantContext.verify(TenantContext::clear);
        mockedBranchContext.verify(BranchContext::clear);
    }

    @Test
    void processImportAsync_ShouldHandleExceptionAndMarkAsFailed() {
        doThrow(new RuntimeException("Import failed")).when(bulkImportService).processRowsAndSaveStatus(jobId, "PATIENT", rows);

        bulkImportAsyncService.processImportAsync(jobId, "PATIENT", rows, tenantId, branchId);

        mockedTenantContext.verify(() -> TenantContext.set(tenantId));
        mockedBranchContext.verify(() -> BranchContext.set(branchId));
        verify(bulkImportService).processRowsAndSaveStatus(jobId, "PATIENT", rows);
        verify(bulkImportService).markJobAsFailed(jobId, "Import failed");
        mockedTenantContext.verify(TenantContext::clear);
        mockedBranchContext.verify(BranchContext::clear);
    }
}
