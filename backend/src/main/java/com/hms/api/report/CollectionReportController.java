package com.hms.api.report;
import org.springframework.security.access.prepost.PreAuthorize;

import com.hms.application.report.modules.CollectionReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/collections")
@PreAuthorize("hasPermission('REPORT_COLLECTION','')")
public class CollectionReportController extends BaseReportController {

    public CollectionReportController(CollectionReportService collectionReportService) {
        super(collectionReportService);
    }
}
