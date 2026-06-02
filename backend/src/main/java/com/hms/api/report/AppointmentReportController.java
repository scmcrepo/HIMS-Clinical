package com.hms.api.report;

import com.hms.application.report.modules.AppointmentReportService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/report/appointments")
public class AppointmentReportController extends BaseReportController {

    public AppointmentReportController(AppointmentReportService appointmentReportService) {
        super(appointmentReportService);
    }
}
