package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getPatientRegistrationDaywise(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT
                p.created_at::DATE                          AS reg_date,
                COUNT(*)                                    AS total_registered,
                COUNT(*) FILTER (WHERE p.gender = 0)  AS male_count,
                COUNT(*) FILTER (WHERE p.gender = 1) AS female_count,
                COUNT(*) FILTER (WHERE p.gender NOT IN (0, 1)) AS other_count
            FROM patients p
            WHERE p.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR p.primary_provider_id::text = ?)
            GROUP BY p.created_at::DATE
            ORDER BY p.created_at::DATE
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, consultantId == null ? "" : consultantId, consultantId == null ? "" : consultantId);
    }

    public List<Map<String, Object>> getPatientRegistrationDetails(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT
                p.created_at::DATE AS "Reg Date",
                sn.value AS "Patient No",
                COALESCE(p.salutation || ' ', '') || p.first_name || ' ' || p.last_name AS "Patient Name",
                CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Gender",
                EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth)) || ' Y' AS "Age",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS "Consultant",
                COALESCE(u.username, '') AS "Registered By"
            FROM patients p
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN consultants c ON p.primary_provider_id = c.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR p.primary_provider_id::text = ?)
            ORDER BY p.created_at::DATE ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, consultantId == null ? "" : consultantId, consultantId == null ? "" : consultantId);
    }

    public List<Map<String, Object>> getConsultwiseRegistration(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT
                p.created_at::DATE AS "Reg Date",
                sn.value AS "Patient No",
                COALESCE(p.salutation || ' ', '') || p.first_name || ' ' || p.last_name AS "Patient Name",
                CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth)) || ' Y' AS "Age",
                p.contact_number AS "Contact No",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS "Consultant",
                COALESCE(u.username, '') AS "Registered By"
            FROM patients p
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN consultants c ON p.primary_provider_id = c.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR c.id::text = ?)
            ORDER BY "Consultant" ASC, p.created_at ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, consultantId == null ? "" : consultantId, consultantId == null ? "" : consultantId);
    }

    public List<Map<String, Object>> getDepartmentwiseRegistration(String fromDate, String toDate, String departmentId) {
        String sql = """
            SELECT
                p.created_at::DATE AS "Reg Date",
                sn.value AS "Patient No",
                COALESCE(p.salutation || ' ', '') || p.first_name || ' ' || p.last_name AS "Patient Name",
                CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth)) || ' Y' AS "Age",
                p.contact_number AS "Contact No",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS "Consultant",
                COALESCE(d.name, '') AS "Department",
                COALESCE(u.username, '') AS "Registered By"
            FROM patients p
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN consultants c ON p.primary_provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR d.id::text = ?)
            ORDER BY "Department" ASC, p.created_at ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, departmentId == null ? "" : departmentId, departmentId == null ? "" : departmentId);
    }
}
