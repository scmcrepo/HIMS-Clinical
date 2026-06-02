package com.hms.api.report;

import com.hms.application.report.modules.DiagnosticsReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/diagnostics")
public class DiagnosticsReportController extends BaseReportController {

    public DiagnosticsReportController(DiagnosticsReportService diagnosticsReportService) {
        super(diagnosticsReportService);
    }
}
