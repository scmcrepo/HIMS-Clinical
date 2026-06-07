package com.hms.api.report;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.application.report.modules.PharmacyReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/pharmacy")
@PreAuthorize("hasPermission('REPORT_PHARMACY','')")
public class PharmacyReportController extends BaseReportController {

    public PharmacyReportController(PharmacyReportService pharmacyReportService) {
        super(pharmacyReportService);
    }
}
