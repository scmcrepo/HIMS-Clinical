package com.hms.api.report;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.application.report.modules.RevenueReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/revenue")
@PreAuthorize("hasPermission('REPORT_REVENUE','')")
public class RevenueReportController extends BaseReportController {

    public RevenueReportController(RevenueReportService revenueReportService) {
        super(revenueReportService);
    }
}
