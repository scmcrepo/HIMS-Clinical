package com.hms.application.report.modules;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DiagnosticsReportDataService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getLabTestsDoneSummary(String fromDate, String toDate) {
        String sql = """
            SELECT
                dd.name                                     AS department,
                COUNT(dol.id) FILTER (WHERE ce.encounter_type = 0 OR ord.encounter_id IS NULL) AS op_done,
                COUNT(dol.id) FILTER (WHERE ce.encounter_type = 1) AS ip_done,
                COUNT(dol.id)                                      AS total_done
            FROM diagnostic_orders ord
            JOIN diagnostic_order_lines dol ON ord.id = dol.order_id
            LEFT JOIN clinical_encounters ce ON ord.encounter_id = ce.id
            JOIN (
                SELECT dt.charge_id AS service_catalog_item_id, MAX(dd.name) AS name
                FROM diagnostic_templates dt
                LEFT JOIN departments dd ON dt.department_id = dd.id
                WHERE dt.charge_id IS NOT NULL
                GROUP BY dt.charge_id
            ) dd ON dol.service_catalog_item_id = dd.service_catalog_item_id
            WHERE ord.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND dol.test_status = 2
            GROUP BY dd.name
            ORDER BY dd.name NULLS LAST
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getLabTestsDoneDetail(String fromDate, String toDate) {
        String sql = """
            SELECT
                dd.name                                     AS department,
                ord.sequence_number                         AS order_no,
                b.bill_number                               AS bill_no,
                b.bill_date                                 AS bill_date,
                sn.value                                    AS patient_no,
                pat.first_name || ' ' || pat.last_name      AS patient,
                c.first_name || ' ' || c.last_name          AS consultant,
                dol.item_name                               AS test_name,
                s.name                                      AS specimen,
                'Completed'                                 AS status
            FROM diagnostic_orders ord
            JOIN diagnostic_order_lines dol ON ord.id = dol.order_id
            LEFT JOIN clinical_encounters ce ON ord.encounter_id = ce.id
            JOIN patients pat ON ord.patient_id = pat.id
            LEFT JOIN number_sequences sn ON pat.id = sn.id
            LEFT JOIN bills b ON ord.bill_id = b.id
            LEFT JOIN consultants c ON COALESCE(ord.provider_id, ce.primary_provider_id, b.primary_provider_id) = c.id
            JOIN (
                SELECT dt.charge_id AS service_catalog_item_id, MAX(dd.name) AS name
                FROM diagnostic_templates dt
                LEFT JOIN departments dd ON dt.department_id = dd.id
                WHERE dt.charge_id IS NOT NULL
                GROUP BY dt.charge_id
            ) dd ON dol.service_catalog_item_id = dd.service_catalog_item_id
            LEFT JOIN specimens s ON dol.specimen_id = s.id
            WHERE dol.test_status = 2
              AND ord.created_at::DATE BETWEEN ?::DATE AND ?::DATE
            ORDER BY dd.name NULLS LAST, ord.created_at ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getPendingLabTestsSummary(String fromDate, String toDate) {
        String sql = """
            SELECT
                dd.name                                     AS department,
                COUNT(dol.id) FILTER (WHERE ce.encounter_type = 0 OR ord.encounter_id IS NULL) AS op_pending,
                COUNT(dol.id) FILTER (WHERE ce.encounter_type = 1) AS ip_pending,
                COUNT(dol.id)                                      AS total_pending
            FROM diagnostic_orders ord
            JOIN diagnostic_order_lines dol ON ord.id = dol.order_id
            LEFT JOIN clinical_encounters ce ON ord.encounter_id = ce.id
            JOIN (
                SELECT dt.charge_id AS service_catalog_item_id, MAX(dd.name) AS name
                FROM diagnostic_templates dt
                LEFT JOIN departments dd ON dt.department_id = dd.id
                WHERE dt.charge_id IS NOT NULL
                GROUP BY dt.charge_id
            ) dd ON dol.service_catalog_item_id = dd.service_catalog_item_id
            WHERE dol.test_status IN (0, 1)
              AND ord.created_at::DATE BETWEEN ?::DATE AND ?::DATE
            GROUP BY dd.name
            ORDER BY dd.name NULLS LAST
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }

    public List<Map<String, Object>> getPendingLabTestsDetail(String fromDate, String toDate) {
        String sql = """
            SELECT
                dd.name                                     AS department,
                ord.sequence_number                         AS order_no,
                b.bill_number                               AS bill_no,
                b.bill_date                                 AS bill_date,
                sn.value                                    AS patient_no,
                pat.first_name || ' ' || pat.last_name      AS patient,
                c.first_name || ' ' || c.last_name          AS consultant,
                dol.item_name                               AS test_name,
                s.name                                      AS specimen,
                CASE WHEN dol.test_status = 0 AND dol.payment_status = 0 THEN 'Report Not Entered'
                     WHEN dol.test_status = 0 AND dol.payment_status = 1 THEN 'Billed - Report Not Entered'
                     WHEN dol.test_status = 0 AND dol.payment_status = 2 THEN 'Partially Paid - Report Not Entered'
                     WHEN dol.test_status = 1 THEN 'Specimen Collected'
                     ELSE 'Pending' END                     AS status
            FROM diagnostic_orders ord
            JOIN diagnostic_order_lines dol ON ord.id = dol.order_id
            LEFT JOIN clinical_encounters ce ON ord.encounter_id = ce.id
            JOIN patients pat ON ord.patient_id = pat.id
            LEFT JOIN number_sequences sn ON pat.id = sn.id
            LEFT JOIN bills b ON ord.bill_id = b.id
            LEFT JOIN consultants c ON COALESCE(ord.provider_id, ce.primary_provider_id, b.primary_provider_id) = c.id
            JOIN (
                SELECT dt.charge_id AS service_catalog_item_id, MAX(dd.name) AS name
                FROM diagnostic_templates dt
                LEFT JOIN departments dd ON dt.department_id = dd.id
                WHERE dt.charge_id IS NOT NULL
                GROUP BY dt.charge_id
            ) dd ON dol.service_catalog_item_id = dd.service_catalog_item_id
            LEFT JOIN specimens s ON dol.specimen_id = s.id
            WHERE dol.test_status IN (0, 1)
              AND ord.created_at::DATE BETWEEN ?::DATE AND ?::DATE
            ORDER BY dd.name NULLS LAST, ord.created_at ASC
            """;
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql, fromDate, toDate);
    }


}
