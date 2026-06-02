package com.hms.api.report;

import com.hms.application.report.modules.CollectionReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/collections")
public class CollectionReportController extends BaseReportController {

    public CollectionReportController(CollectionReportService collectionReportService) {
        super(collectionReportService);
    }
}
