package com.hms.api.report;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.application.report.modules.ProcurementReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/procurement")
@PreAuthorize("hasPermission('REPORT_PROCUREMENT','')")
public class ProcurementReportController extends BaseReportController {

    public ProcurementReportController(ProcurementReportService procurementReportService) {
        super(procurementReportService);
    }
}
