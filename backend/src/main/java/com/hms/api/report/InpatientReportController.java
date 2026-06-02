package com.hms.api.report;

import com.hms.application.report.modules.InpatientReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/inpatient")
public class InpatientReportController extends BaseReportController {

    public InpatientReportController(InpatientReportService inpatientReportService) {
        super(inpatientReportService);
    }
}
