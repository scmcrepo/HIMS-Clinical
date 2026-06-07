package com.hms.api.report;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.application.report.modules.PatientReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/patient")
@PreAuthorize("hasPermission('REPORT_PATIENT','')")
public class PatientReportController extends BaseReportController {

    public PatientReportController(PatientReportService patientReportService) {
        super(patientReportService);
    }
}
