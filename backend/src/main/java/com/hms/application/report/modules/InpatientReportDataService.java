package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InpatientReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getAdmissionsReport(String fromDate, String toDate) {
        String sql = """
            SELECT
                ROW_NUMBER() OVER (ORDER BY ce.started_at DESC) AS "S.No",
                sn_pat.value                                AS "Patient No",
                TO_CHAR(ce.started_at, 'DD/MM/YYYY')        AS "Admission Date",
                COALESCE(pat.salutation || ' ', '') || pat.first_name || ' ' || pat.last_name AS "Patient Name",
                EXTRACT(YEAR FROM age(CURRENT_DATE, pat.estimated_date_of_birth)) || ' Y' AS "Age",
                CASE pat.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Gender",
                COALESCE(c.first_name || ' ' || c.last_name, '') AS "Consultant",
                COALESCE(d.name, INITCAP(c.specialisation), '') AS "Department",
                COALESCE(bed.name, '')                      AS "Bed No",
                COALESCE(rc.name, '')                       AS "Ward",
                COALESCE(u.username, 'App Admin')           AS "Registered By"
            FROM clinical_encounters ce
            JOIN patients pat ON ce.patient_id = pat.id
            LEFT JOIN consultants c ON ce.primary_provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            LEFT JOIN beds bed ON ce.last_bed_id = bed.id
            LEFT JOIN room_categories rc ON bed.room_category_id = rc.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON ce.created_by = u.id
            WHERE ce.started_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND ce.visit_mode = 1
            ORDER BY ce.started_at DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getAdmissionsSummaryReport(String fromDate, String toDate) {
        String sql = """
            SELECT
                COALESCE(d.name, INITCAP(c.specialisation), 'No Department') AS department,
                COUNT(CASE WHEN pat.gender = 0 THEN 1 END)  AS male,
                COUNT(CASE WHEN pat.gender = 1 THEN 1 END)  AS female,
                COUNT(ce.id)                                AS total
            FROM clinical_encounters ce
            JOIN patients pat ON ce.patient_id = pat.id
            LEFT JOIN consultants c ON ce.primary_provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            WHERE ce.started_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND ce.visit_mode = 1
            GROUP BY COALESCE(d.name, INITCAP(c.specialisation), 'No Department')
            ORDER BY department ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getDischargesReport(String fromDate, String toDate) {
        String sql = """
            SELECT
                TO_CHAR(pat.created_at, 'DD/MM/YYYY') AS "Reg Date",
                sn_pat.value AS "Patient No",
                COALESCE(pat.salutation || ' ', '') || pat.first_name || ' ' || pat.last_name AS "Patient Name",
                TO_CHAR(ce.started_at, 'DD/MM/YYYY') AS "Admission Date",
                TO_CHAR(COALESCE(bo.to_datetime, ce.discharged_at), 'DD/MM/YYYY') AS "Discharge Date",
                COALESCE(b.name, '') AS "Bed No",
                COALESCE(c.salutation || ' ', '') || c.first_name || ' ' || c.last_name AS "Consultant",
                COALESCE(u.username, 'App Admin') AS "Registered By",
                pat.gender AS gender
            FROM clinical_encounters ce
            JOIN patients pat ON ce.patient_id = pat.id
            LEFT JOIN consultants c ON ce.primary_provider_id = c.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON ce.created_by = u.id
            LEFT JOIN LATERAL (
                SELECT bo_inner.to_datetime, bo_inner.bed_id
                FROM bed_occupancies bo_inner
                WHERE bo_inner.encounter_id = ce.id
                  AND bo_inner.to_datetime IS NOT NULL
                  AND bo_inner.status = 0
                ORDER BY bo_inner.to_datetime DESC
                LIMIT 1
            ) bo ON true
            LEFT JOIN beds b ON bo.bed_id = b.id
            WHERE ce.visit_mode = 1
              AND COALESCE(bo.to_datetime, ce.discharged_at) IS NOT NULL
              AND COALESCE(bo.to_datetime, ce.discharged_at)::DATE BETWEEN ?::DATE AND ?::DATE
            ORDER BY COALESCE(bo.to_datetime, ce.discharged_at) DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getBedOccupancyPeriodReport(String fromDate, String toDate) {
        String sql = """
            WITH params AS (
                SELECT ?::DATE AS p_start, ?::DATE AS p_end
            ),
            period_days AS (
                SELECT p_start, p_end, (p_end - p_start + 1) AS num_days FROM params
            ),
            occupancy_durations AS (
                SELECT
                    b.id AS bed_id,
                    b.room_category_id,
                    bo.id AS occupancy_id,
                    GREATEST(bo.from_datetime::DATE, p_start) AS overlap_start,
                    LEAST(COALESCE(bo.to_datetime::DATE, CURRENT_DATE), p_end) AS overlap_end
                FROM beds b
                CROSS JOIN params
                LEFT JOIN bed_occupancies bo ON b.id = bo.bed_id
                    AND bo.from_datetime::DATE <= p_end
                    AND COALESCE(bo.to_datetime::DATE, CURRENT_DATE) >= p_start
            ),
            bed_occupied_days AS (
                SELECT
                    room_category_id,
                    SUM(
                        CASE
                            WHEN occupancy_id IS NOT NULL AND overlap_end >= overlap_start THEN
                                (overlap_end - overlap_start + 1)
                            ELSE 0
                        END
                    ) AS occupied_days
                FROM occupancy_durations
                GROUP BY room_category_id
            ),
            beds_count AS (
                SELECT room_category_id, COUNT(*) AS total_beds
                FROM beds
                GROUP BY room_category_id
            )
            SELECT
                TO_CHAR(p_start, 'YYYY-MM') AS period,
                rc.name AS ward,
                bc.total_beds AS total_beds,
                COALESCE(bod.occupied_days, 0) AS occupied_days,
                pd.num_days AS num_days,
                ROUND(
                    (COALESCE(bod.occupied_days, 0) * 100.0 / NULLIF(bc.total_beds * pd.num_days, 0))::numeric,
                    2
                ) AS occupancy_pct
            FROM room_categories rc
            JOIN beds_count bc ON rc.id = bc.room_category_id
            CROSS JOIN period_days pd
            LEFT JOIN bed_occupied_days bod ON rc.id = bod.room_category_id
            ORDER BY ward
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getBedsTransferredReport(String fromDate, String toDate) {
        String sql = """
            SELECT
                ROW_NUMBER() OVER (ORDER BY bo_curr.from_datetime DESC) AS "S.No",
                TO_CHAR(bo_curr.from_datetime, 'DD/MM/YYYY')          AS "Transfer Date",
                sn_pat.value                                          AS "Patient No",
                COALESCE(pat.salutation || ' ', '') || pat.first_name || ' ' || pat.last_name AS "Patient Name",
                EXTRACT(YEAR FROM age(CURRENT_DATE, pat.estimated_date_of_birth)) || ' Y' AS "Age",
                CASE pat.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Gender",
                b_from.name                                           AS "Bed Transfer From",
                rc_from.name                                          AS "Ward Transfer From",
                b_to.name                                             AS "Bed Transfer To",
                rc_to.name                                            AS "Ward Transfer To",
                COALESCE(u.username, 'App Admin')                     AS "Registered By"
            FROM bed_occupancies bo_curr
            JOIN clinical_encounters ce ON bo_curr.encounter_id = ce.id
            JOIN patients pat ON ce.patient_id = pat.id
            JOIN beds b_to ON bo_curr.bed_id = b_to.id
            JOIN room_categories rc_to ON b_to.room_category_id = rc_to.id
            LEFT JOIN number_sequences sn_pat ON pat.id = sn_pat.id
            LEFT JOIN users u ON bo_curr.created_by = u.id
            LEFT JOIN LATERAL (
                SELECT bo_prev.bed_id
                FROM bed_occupancies bo_prev
                WHERE bo_prev.encounter_id = bo_curr.encounter_id
                  AND bo_prev.to_datetime IS NOT NULL
                  AND bo_prev.to_datetime <= bo_curr.from_datetime
                  AND bo_prev.id != bo_curr.id
                ORDER BY bo_prev.to_datetime DESC
                LIMIT 1
            ) prev ON true
            LEFT JOIN beds b_from ON prev.bed_id = b_from.id
            LEFT JOIN room_categories rc_from ON b_from.room_category_id = rc_from.id
            WHERE bo_curr.from_datetime::DATE BETWEEN ?::DATE AND ?::DATE
              AND prev.bed_id IS NOT NULL
              AND prev.bed_id != bo_curr.bed_id
            ORDER BY bo_curr.from_datetime DESC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getBedOccupancy() {
        String sql = """
            SELECT rc.name as ward_name, b.name as bed_number, b.bed_status as status,
                   pat.first_name || ' ' || pat.last_name as patient_name,
                   sn.value as patient_number
            FROM beds b
            JOIN room_categories rc ON b.room_category_id = rc.id
            LEFT JOIN bed_occupancies bo ON b.id = bo.bed_id AND bo.to_datetime IS NULL
            LEFT JOIN clinical_encounters ce ON bo.encounter_id = ce.id
            LEFT JOIN patients pat ON ce.patient_id = pat.id
            LEFT JOIN number_sequences sn ON pat.id = sn.id
            ORDER BY rc.name, b.name
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql);
    }

    public Map<String, Object> getDischargeSummary(UUID encounterId) {
        if (encounterId == null) return Collections.emptyMap();
        String sql = """
            SELECT ce.id as encounter_id, ce.started_at as admission_date, ce.discharged_at,
                   ce.diagnosis, pat.first_name || ' ' || pat.last_name as patient_name,
                   sn.value as patient_number, pat.gender, pat.estimated_date_of_birth,
                   con.first_name || ' ' || con.last_name as consultant_name
            FROM clinical_encounters ce
            JOIN patients pat ON ce.patient_id = pat.id
            LEFT JOIN number_sequences sn ON pat.id = sn.id
            LEFT JOIN consultants con ON ce.primary_provider_id = con.id
            WHERE ce.id = ?
            """;
        try {
            return jdbcTemplate.queryForMap(sql, encounterId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("No clinical encounter found with ID: " + encounterId);
        }
    }
}
