package com.hms.api.report;

import com.hms.application.report.modules.EncounterReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/encounters")
public class EncounterReportController extends BaseReportController {

    public EncounterReportController(EncounterReportService encounterReportService) {
        super(encounterReportService);
    }
}
