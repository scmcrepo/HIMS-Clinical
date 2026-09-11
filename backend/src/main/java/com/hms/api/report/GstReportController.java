package com.hms.api.report;

import com.hms.application.report.modules.GstReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/gst")
@PreAuthorize("hasPermission('REPORT_GST','')")
public class GstReportController extends BaseReportController {

    public GstReportController(GstReportService gstReportService) {
        super(gstReportService);
    }
}
