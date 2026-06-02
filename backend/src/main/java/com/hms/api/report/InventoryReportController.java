package com.hms.api.report;

import com.hms.application.report.modules.InventoryReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/inventory")
public class InventoryReportController extends BaseReportController {

    public InventoryReportController(InventoryReportService inventoryReportService) {
        super(inventoryReportService);
    }
}
