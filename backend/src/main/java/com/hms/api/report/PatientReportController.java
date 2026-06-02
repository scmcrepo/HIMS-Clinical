package com.hms.api.report;

import com.hms.application.report.modules.PatientReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/patient")
public class PatientReportController extends BaseReportController {

    public PatientReportController(PatientReportService patientReportService) {
        super(patientReportService);
    }
}
