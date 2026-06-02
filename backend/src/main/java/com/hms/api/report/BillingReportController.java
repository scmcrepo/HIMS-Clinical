package com.hms.api.report;

import com.hms.application.report.modules.BillingReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/billing")
public class BillingReportController extends BaseReportController {

    public BillingReportController(BillingReportService billingReportService) {
        super(billingReportService);
    }
}
