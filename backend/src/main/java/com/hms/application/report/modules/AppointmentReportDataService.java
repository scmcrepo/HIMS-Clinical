package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AppointmentReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getAppointmentsDaywise(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT
                a.appointment_date                          AS appt_date,
                COUNT(*)                                    AS total_booked,
                COUNT(*) FILTER (WHERE a.appointment_status = 0) AS booked_count,
                COUNT(*) FILTER (WHERE a.appointment_status = 1) AS rescheduled_count,
                COUNT(*) FILTER (WHERE a.appointment_status = 2) AS checked_in_count,
                COUNT(*) FILTER (WHERE a.appointment_status = 3) AS cancelled_count
            FROM appointments a
            WHERE a.appointment_date BETWEEN ?::DATE AND ?::DATE
              AND a.appointment_status != 3
              AND (? = '' OR a.provider_id::text = ?)
            GROUP BY a.appointment_date
            ORDER BY a.appointment_date
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, consultantId == null ? "" : consultantId, consultantId == null ? "" : consultantId);
    }

    public List<Map<String, Object>> getAppointmentScheduledDetails(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT 
                a.appointment_date::DATE AS "Date",
                COALESCE(to_char(s.from_time::time, 'HH12:MI AM') || ' - ' || to_char(s.to_time::time, 'HH12:MI AM'), to_char(a.appointment_time, 'HH12:MI AM'), '') AS "Time Slot",
                COALESCE(COALESCE(p.salutation || ' ', '') || p.first_name || ' ' || p.last_name || COALESCE(' (' || sn.value || ')', ''), COALESCE(a.temp_patient_salutation || ' ', '') || a.temp_patient_name) AS "Patient",
                COALESCE(CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END, a.temp_patient_gender) AS "Sex",
                COALESCE(
                    CASE
                        WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 year'
                            THEN EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'y'
                        WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 month'
                            THEN EXTRACT(MONTH FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'm'
                        ELSE
                            EXTRACT(DAY FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'd'
                    END,
                    CASE WHEN a.temp_patient_age IS NOT NULL THEN a.temp_patient_age::text || 'y' ELSE NULL END,
                    ''
                ) AS "Age",
                COALESCE(p.contact_number, a.temp_patient_phone) AS "Contact",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS "Consultant",
                COALESCE(u.username, '') AS "Booked By"
            FROM appointments a
            LEFT JOIN patients p ON a.patient_id = p.id
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN appointment_slots s ON a.slot_id = s.id
            LEFT JOIN consultants c ON a.provider_id = c.id
            LEFT JOIN users u ON a.created_by = u.id
            WHERE a.appointment_date BETWEEN ?::DATE AND ?::DATE
              AND a.appointment_status != 3
              AND (? = '' OR a.provider_id::text = ?)
            ORDER BY a.appointment_date ASC, a.appointment_time ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, consultantId == null ? "" : consultantId, consultantId == null ? "" : consultantId);
    }
 
    public List<Map<String, Object>> getAppointmentCancelledDetails(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT 
                a.appointment_date::DATE AS "Date",
                COALESCE(to_char(s.from_time::time, 'HH12:MI AM') || ' - ' || to_char(s.to_time::time, 'HH12:MI AM'), to_char(a.appointment_time, 'HH12:MI AM'), '') AS "Time Slot",
                COALESCE(COALESCE(p.salutation || ' ', '') || p.first_name || ' ' || p.last_name || COALESCE(' (' || sn.value || ')', ''), COALESCE(a.temp_patient_salutation || ' ', '') || a.temp_patient_name) AS "Patient",
                COALESCE(CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END, a.temp_patient_gender) AS "Sex",
                COALESCE(
                    CASE
                        WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 year'
                            THEN EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'y'
                        WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 month'
                            THEN EXTRACT(MONTH FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'm'
                        ELSE
                            EXTRACT(DAY FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'd'
                    END,
                    CASE WHEN a.temp_patient_age IS NOT NULL THEN a.temp_patient_age::text || 'y' ELSE NULL END,
                    ''
                ) AS "Age",
                COALESCE(p.contact_number, a.temp_patient_phone) AS "Contact",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS "Consultant",
                COALESCE(u.username, '') AS "Cancelled By"
            FROM appointments a
            LEFT JOIN patients p ON a.patient_id = p.id
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN appointment_slots s ON a.slot_id = s.id
            LEFT JOIN consultants c ON a.provider_id = c.id
            LEFT JOIN users u ON a.created_by = u.id
            WHERE a.appointment_date BETWEEN ?::DATE AND ?::DATE
              AND a.appointment_status = 3
              AND (? = '' OR a.provider_id::text = ?)
            ORDER BY a.appointment_date ASC, a.appointment_time ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, consultantId == null ? "" : consultantId, consultantId == null ? "" : consultantId);
    }

    public List<Map<String, Object>> getAppointmentsConsultantwise(String fromDate, String toDate) {
        String sql = """
            SELECT
                c.first_name || ' ' || c.last_name         AS consultant_name,
                d.name                                      AS department,
                COUNT(*)                                    AS total_appointments,
                COUNT(*) FILTER (WHERE a.appointment_status = 2) AS checked_in,
                COUNT(*) FILTER (WHERE a.appointment_status = 3) AS cancelled
            FROM appointments a
            JOIN consultants c ON a.provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            WHERE a.appointment_date BETWEEN ?::DATE AND ?::DATE
              AND a.appointment_status != 3
            GROUP BY c.id, c.first_name, c.last_name, d.name
            ORDER BY total_appointments DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getAppointmentsCancelledDaywise(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT
                a.appointment_date                          AS appt_date,
                COUNT(*)                                    AS cancelled_count
            FROM appointments a
            WHERE a.appointment_date BETWEEN ?::DATE AND ?::DATE
              AND a.appointment_status = 3
              AND (? = '' OR a.provider_id::text = ?)
            GROUP BY a.appointment_date
            ORDER BY a.appointment_date
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, consultantId == null ? "" : consultantId, consultantId == null ? "" : consultantId);
    }

    public List<Map<String, Object>> getAppointmentsCancelledConsultantwise(String fromDate, String toDate) {
        String sql = """
            SELECT
                c.first_name || ' ' || c.last_name         AS consultant_name,
                d.name                                      AS department,
                COUNT(*)                                    AS cancelled_count
            FROM appointments a
            JOIN consultants c ON a.provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            WHERE a.appointment_date BETWEEN ?::DATE AND ?::DATE
              AND a.appointment_status = 3
            GROUP BY c.id, c.first_name, c.last_name, d.name
            ORDER BY cancelled_count DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }
}
