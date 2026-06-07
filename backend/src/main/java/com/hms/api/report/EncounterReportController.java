package com.hms.api.report;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.application.report.modules.EncounterReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/encounters")
@PreAuthorize("hasPermission('REPORT_ENCOUNTER','')")
public class EncounterReportController extends BaseReportController {

    public EncounterReportController(EncounterReportService encounterReportService) {
        super(encounterReportService);
    }
}
