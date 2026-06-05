package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EncounterReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getEncountersReport(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT
                ce.started_at::DATE                         AS encounter_date,
                sn_enc.value                      AS encounter_number,
                pat.first_name || ' ' || pat.last_name     AS patient_name,
                sn_pat.value                      AS patient_number,
                c.first_name || ' ' || c.last_name         AS consultant_name,
                d.name                                      AS department,
                CASE ce.visit_mode WHEN 0 THEN 'OUTPATIENT' WHEN 1 THEN 'INPATIENT' ELSE 'OTHER' END AS visit_type,
                CASE ce.status WHEN 1 THEN 'ACTIVE' WHEN 2 THEN 'DISCHARGED' ELSE ce.status::text END AS encounter_status,
                ce.diagnosis
            FROM clinical_encounters ce
            JOIN patients pat ON ce.patient_id = pat.id
            LEFT JOIN consultants c ON ce.primary_provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            LEFT JOIN number_sequences sn_enc ON ce.id = sn_enc.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            WHERE ce.started_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR c.id::text = ?)
            ORDER BY ce.started_at DESC
            """;
        String cid = consultantId == null ? "" : consultantId;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, cid, cid);
    }

    public List<Map<String, Object>> getVisitDetails(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT
                ce.started_at::DATE AS "Reg Date",
                sn.value AS "Patient No",
                COALESCE(p.salutation || ' ', '') || p.first_name || ' ' || p.last_name AS "Patient Name",
                CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                CASE
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS "Consultant",
                COALESCE(u.username, '') AS "Registered By"
            FROM clinical_encounters ce
            JOIN patients p ON ce.patient_id = p.id
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN consultants c ON ce.primary_provider_id = c.id
            LEFT JOIN users u ON ce.created_by = u.id
            WHERE ce.started_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR c.id::text = ?)
            ORDER BY ce.started_at::DATE ASC
            """;
        String cid = consultantId == null ? "" : consultantId;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, cid, cid);
    }

    public List<Map<String, Object>> getDepartmentWiseVisitReport(String fromDate, String toDate) {
        String sql = """
            WITH visit_ranks AS (
                SELECT 
                    ce.primary_provider_id,
                    c.department_id,
                    ce.started_at::DATE as visit_date,
                    ROW_NUMBER() OVER(PARTITION BY ce.patient_id, ce.primary_provider_id ORDER BY ce.started_at ASC) as visit_num
                FROM clinical_encounters ce
                JOIN consultants c ON ce.primary_provider_id = c.id
            )
            SELECT 
                COALESCE(COALESCE(d.name, 'Unassigned'), 'Grand Total') AS "Department",
                MAX(d.id::text) AS department_id,
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''), CASE WHEN COALESCE(d.name, 'Unassigned') IS NOT NULL THEN 'Total' ELSE '' END) AS "Consultant",
                COUNT(*) FILTER (WHERE v.visit_num = 1) AS "New Patients",
                COUNT(*) FILTER (WHERE v.visit_num > 1) AS "Old Patients",
                COUNT(*) AS "Total"
            FROM visit_ranks v
            JOIN consultants c ON v.primary_provider_id = c.id
            LEFT JOIN departments d ON v.department_id = d.id
            WHERE v.visit_date BETWEEN ?::DATE AND ?::DATE
            GROUP BY ROLLUP(COALESCE(d.name, 'Unassigned'), c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''))
            ORDER BY COALESCE(d.name, 'Unassigned') NULLS LAST, (c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, '')) NULLS LAST
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getDepartmentWiseConsultedReport(String fromDate, String toDate) {
        String sql = """
            SELECT 
                COALESCE(d.name, 'Grand Total') AS "Department",
                COUNT(*) AS "Encounter",
                COUNT(*) FILTER (WHERE ce.encounter_status >= 2 OR ce.status = 2) AS "Consulted"
            FROM clinical_encounters ce
            LEFT JOIN consultants c ON ce.primary_provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            WHERE ce.started_at::DATE BETWEEN ?::DATE AND ?::DATE
            GROUP BY ROLLUP(d.name)
            ORDER BY d.name NULLS LAST
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getConsultationSummaryReport(String fromDate, String toDate) {
        String sql = """
            SELECT
                d.name AS "Department",
                c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', '') AS "Consultant",
                COUNT(*) FILTER (WHERE pat.gender = 0) AS "No of Male",
                COUNT(*) FILTER (WHERE pat.gender = 1) AS "No of Female",
                COUNT(*) AS "Total No of Patients"
            FROM clinical_encounters ce
            JOIN patients pat ON ce.patient_id = pat.id
            JOIN consultants c ON ce.primary_provider_id = c.id
            JOIN departments d ON c.department_id = d.id
            WHERE ce.started_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (ce.encounter_status >= 2 OR ce.status = 2)
            GROUP BY d.name, c.first_name, c.last_name, c.qualification
            ORDER BY d.name ASC, c.first_name ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getConsultantWiseVisitReport(String fromDate, String toDate) {
        String sql = """
            WITH visit_ranks AS (
                SELECT 
                    ce.primary_provider_id,
                    ce.started_at::DATE as visit_date,
                    ROW_NUMBER() OVER(PARTITION BY ce.patient_id, ce.primary_provider_id ORDER BY ce.started_at ASC) as visit_num
                FROM clinical_encounters ce
                WHERE ce.primary_provider_id IS NOT NULL
            )
            SELECT 
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''), 'Total') AS "Consultant",
                MAX(c.id::text) AS consultant_id,
                COUNT(*) FILTER (WHERE v.visit_num = 1) AS "New Patients",
                COUNT(*) FILTER (WHERE v.visit_num > 1) AS "Old Patients",
                COUNT(*) AS "Total"
            FROM visit_ranks v
            JOIN consultants c ON v.primary_provider_id = c.id
            WHERE v.visit_date BETWEEN ?::DATE AND ?::DATE
            GROUP BY ROLLUP(c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''))
            ORDER BY (c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, '')) NULLS LAST
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getConsultantWiseConsultedReport(String fromDate, String toDate, String department) {
        String sql = """
            SELECT 
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''), 'Total') AS "Consultant",
                MAX(c.id::text) AS consultant_id,
                COUNT(*) AS "Encounter",
                COUNT(*) FILTER (WHERE ce.encounter_status >= 2 OR ce.status = 2) AS "Consulted"
            FROM clinical_encounters ce
            LEFT JOIN consultants c ON ce.primary_provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            WHERE ce.started_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND COALESCE(d.name, 'Grand Total') = ?
            GROUP BY ROLLUP(c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''))
            ORDER BY (c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, '')) NULLS LAST
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, department);
    }

    public List<Map<String, Object>> getConsultantWiseVisitDetail(String fromDate, String toDate, String consultantId) {
        String sql = """
            SELECT
                ce.started_at::DATE AS "Reg Date",
                sn.value AS "Patient No",
                COALESCE(p.salutation || ' ', '') || p.first_name || ' ' || p.last_name AS "Patient Name",
                CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Gender",
                CASE
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' (' || c.qualification || ')', ''), '') AS "Consultant",
                COALESCE(u.username, '') AS "Registered By"
            FROM clinical_encounters ce
            JOIN patients p ON ce.patient_id = p.id
            LEFT JOIN number_sequences sn ON p.id = sn.id
            JOIN consultants c ON ce.primary_provider_id = c.id
            LEFT JOIN users u ON ce.created_by = u.id
            WHERE ce.started_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND c.id = ?::UUID
            ORDER BY ce.started_at::DATE ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate, consultantId);
    }

    public List<Map<String, Object>> getDeptWiseConsultantVisit(String fromDate, String toDate, String departmentId) {
        String sql = """
            WITH visit_ranks AS (
                SELECT 
                    ce.primary_provider_id,
                    ce.started_at::DATE as visit_date,
                    ROW_NUMBER() OVER(PARTITION BY ce.patient_id, ce.primary_provider_id ORDER BY ce.started_at ASC) as visit_num
                FROM clinical_encounters ce
                JOIN consultants c ON ce.primary_provider_id = c.id
                WHERE c.department_id = ?::UUID
            )
            SELECT 
                COALESCE(c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''), 'Total') AS "Consultant",
                MAX(c.id::text) AS consultant_id,
                COUNT(*) FILTER (WHERE v.visit_num = 1) AS "New Patients",
                COUNT(*) FILTER (WHERE v.visit_num > 1) AS "Old Patients",
                COUNT(*) AS "Total"
            FROM visit_ranks v
            JOIN consultants c ON v.primary_provider_id = c.id
            WHERE v.visit_date BETWEEN ?::DATE AND ?::DATE
            GROUP BY ROLLUP(c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, ''))
            ORDER BY (c.first_name || ' ' || c.last_name || COALESCE(' ' || c.qualification, '')) NULLS LAST
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, departmentId, fromDate, toDate);
    }
}
