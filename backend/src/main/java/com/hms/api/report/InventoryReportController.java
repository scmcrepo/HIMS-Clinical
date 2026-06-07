package com.hms.api.report;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.application.report.modules.InventoryReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/inventory")
@PreAuthorize("hasPermission('REPORT_INVENTORY','')")
public class InventoryReportController extends BaseReportController {

    public InventoryReportController(InventoryReportService inventoryReportService) {
        super(inventoryReportService);
    }
}
