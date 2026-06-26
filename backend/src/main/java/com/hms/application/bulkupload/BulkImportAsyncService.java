package com.hms.application.bulkupload;

import com.hms.infrastructure.tenant.BranchContext;
import com.hms.infrastructure.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportAsyncService {

    private final BulkImportService bulkImportService;

    @Async("asyncExecutor")
    public void processImportAsync(UUID jobId, String entityType, List<Map<String, String>> rows, UUID tenantId, UUID branchId) {
        TenantContext.set(tenantId);
        BranchContext.set(branchId);
        log.info("Starting async import job {}", jobId);
        try {
            bulkImportService.processRowsAndSaveStatus(jobId, entityType, rows);
        } catch (Exception e) {
            log.error("Unhandled error in async import job {}", jobId, e);
            bulkImportService.markJobAsFailed(jobId, e.getMessage());
        } finally {
            TenantContext.clear();
            BranchContext.clear();
            log.info("Finished async import job {}", jobId);
        }
    }
}
