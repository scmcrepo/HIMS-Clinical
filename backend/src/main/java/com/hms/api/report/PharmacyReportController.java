package com.hms.api.report;

import com.hms.application.report.modules.PharmacyReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/pharmacy")
public class PharmacyReportController extends BaseReportController {

    public PharmacyReportController(PharmacyReportService pharmacyReportService) {
        super(pharmacyReportService);
    }
}
