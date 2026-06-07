package com.hms.api.report;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.application.report.modules.DiagnosticsReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/diagnostics")
@PreAuthorize("hasPermission('REPORT_DIAGNOSTICS','')")
public class DiagnosticsReportController extends BaseReportController {

    public DiagnosticsReportController(DiagnosticsReportService diagnosticsReportService) {
        super(diagnosticsReportService);
    }
}
