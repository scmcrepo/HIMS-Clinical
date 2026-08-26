package com.hms.api.report;

import com.hms.application.report.modules.InsuranceReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The ten insurance MIS reports (WO-021).
 *
 * <p>Guarded by its own {@code REPORT_INSURANCE} feature key rather than
 * {@code REPORT_BILLING}: these reports expose every patient's claim value,
 * sanctioned limit and disallowance history, and a hospital should be able to
 * give finance staff insurance reports without also giving them billing
 * reports. Seeded for existing tenants by V199 and wired into
 * {@code TenantService} for future ones.
 */
@RestController
@RequestMapping("/report/insurance")
@PreAuthorize("hasPermission('REPORT_INSURANCE','') or hasPermission('INSURANCE_REPORTS','') or hasPermission('INSURANCE','')")
public class InsuranceReportController extends BaseReportController {

    public InsuranceReportController(InsuranceReportService insuranceReportService) {
        super(insuranceReportService);
    }
}
