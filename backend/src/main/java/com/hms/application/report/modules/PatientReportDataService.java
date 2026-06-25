package com.hms.application.report.modules;

import com.hms.application.report.util.ReportScope;
import com.hms.security.encryption.PiiEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientReportDataService {

    private final JdbcTemplate jdbcTemplate;
    private final ReportScope scope;
    private final PiiEncryptionService piiEncryptionService;

    private String decrypt(String base64Ciphertext) {
        if (base64Ciphertext == null || base64Ciphertext.trim().isEmpty()) {
            return "";
        }
        try {
            return piiEncryptionService.decrypt(base64Ciphertext);
        } catch (Exception e) {
            return base64Ciphertext;
        }
    }

    public List<Map<String, Object>> getPatientRegistrationDaywise(String fromDate, String toDate, String consultantId) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                p.created_at::DATE                          AS reg_date,
                COUNT(*)                                    AS total_registered,
                COUNT(*) FILTER (WHERE p.gender = 0)  AS male_count,
                COUNT(*) FILTER (WHERE p.gender = 1) AS female_count,
                COUNT(*) FILTER (WHERE p.gender NOT IN (0, 1)) AS other_count
            FROM patients p
            WHERE p.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR p.primary_provider_id::text = ?)
            """);
        String cid = consultantId == null ? "" : consultantId;
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate, cid, cid));
        sql.append(scope.predicate("p")); args.addAll(scope.args());
        sql.append(" GROUP BY p.created_at::DATE ORDER BY p.created_at::DATE");
        return com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> getPatientRegistrationDetails(String fromDate, String toDate, String consultantId) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                p.created_at::DATE AS "Reg Date",
                sn.value AS "Patient No",
                p.salutation AS "salutation",
                p.first_name AS "first_name",
                p.last_name AS "last_name",
                CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Gender",
                CASE
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                c.first_name AS "c_first_name",
                c.last_name AS "c_last_name",
                c.qualification AS "c_qualification",
                COALESCE(u.username, '') AS "Registered By"
            FROM patients p
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN consultants c ON p.primary_provider_id = c.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR p.primary_provider_id::text = ?)
            """);
        String cid = consultantId == null ? "" : consultantId;
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate, cid, cid));
        sql.append(scope.predicate("p")); args.addAll(scope.args());
        sql.append(" ORDER BY p.created_at::DATE ASC");

        List<Map<String, Object>> rawRows = com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
        List<Map<String, Object>> processedRows = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            newRow.put("Reg Date", row.get("Reg Date"));
            newRow.put("Patient No", row.get("Patient No"));

            // Decrypt Patient Name
            String salutation = (String) row.get("salutation");
            String firstName = decrypt((String) row.get("first_name"));
            String lastName = decrypt((String) row.get("last_name"));
            String patientName = "";
            if (salutation != null && !salutation.isBlank()) {
                patientName += salutation + " ";
            }
            if (firstName != null && !firstName.isBlank()) {
                patientName += firstName;
            }
            if (lastName != null && !lastName.isBlank()) {
                patientName += (patientName.isEmpty() ? "" : " ") + lastName;
            }
            newRow.put("Patient Name", patientName.trim());

            newRow.put("Gender", row.get("Gender"));
            newRow.put("Age", row.get("Age"));

            // Decrypt Consultant Name
            String cFirstName = decrypt((String) row.get("c_first_name"));
            String cLastName = decrypt((String) row.get("c_last_name"));
            String cQual = (String) row.get("c_qualification");
            String consultant = "";
            if (cFirstName != null && !cFirstName.isBlank()) {
                consultant += cFirstName;
            }
            if (cLastName != null && !cLastName.isBlank()) {
                consultant += (consultant.isEmpty() ? "" : " ") + cLastName;
            }
            if (cQual != null && !cQual.isBlank()) {
                consultant += " (" + cQual + ")";
            }
            newRow.put("Consultant", consultant.trim());

            newRow.put("Registered By", row.get("Registered By"));
            processedRows.add(newRow);
        }
        return processedRows;
    }

    public List<Map<String, Object>> getConsultwiseRegistration(String fromDate, String toDate, String consultantId) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                p.created_at::DATE AS "Reg Date",
                sn.value AS "Patient No",
                p.salutation AS "salutation",
                p.first_name AS "first_name",
                p.last_name AS "last_name",
                CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                CASE
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                p.contact_number AS "contact_number",
                c.first_name AS "c_first_name",
                c.last_name AS "c_last_name",
                c.qualification AS "c_qualification",
                COALESCE(u.username, '') AS "Registered By"
            FROM patients p
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN consultants c ON p.primary_provider_id = c.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR c.id::text = ?)
            """);
        String cid = consultantId == null ? "" : consultantId;
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate, cid, cid));
        sql.append(scope.predicate("p")); args.addAll(scope.args());
        sql.append(" ORDER BY \"Consultant\" ASC, p.created_at ASC");

        List<Map<String, Object>> rawRows = com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
        List<Map<String, Object>> processedRows = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            newRow.put("Reg Date", row.get("Reg Date"));
            newRow.put("Patient No", row.get("Patient No"));

            // Decrypt Patient Name
            String salutation = (String) row.get("salutation");
            String firstName = decrypt((String) row.get("first_name"));
            String lastName = decrypt((String) row.get("last_name"));
            String patientName = "";
            if (salutation != null && !salutation.isBlank()) {
                patientName += salutation + " ";
            }
            if (firstName != null && !firstName.isBlank()) {
                patientName += firstName;
            }
            if (lastName != null && !lastName.isBlank()) {
                patientName += (patientName.isEmpty() ? "" : " ") + lastName;
            }
            newRow.put("Patient Name", patientName.trim());

            newRow.put("Sex", row.get("Sex"));
            newRow.put("Age", row.get("Age"));

            // Decrypt Contact Number
            newRow.put("Contact No", decrypt((String) row.get("contact_number")));

            // Decrypt Consultant Name
            String cFirstName = decrypt((String) row.get("c_first_name"));
            String cLastName = decrypt((String) row.get("c_last_name"));
            String cQual = (String) row.get("c_qualification");
            String consultant = "";
            if (cFirstName != null && !cFirstName.isBlank()) {
                consultant += cFirstName;
            }
            if (cLastName != null && !cLastName.isBlank()) {
                consultant += (consultant.isEmpty() ? "" : " ") + cLastName;
            }
            if (cQual != null && !cQual.isBlank()) {
                consultant += " (" + cQual + ")";
            }
            newRow.put("Consultant", consultant.trim());

            newRow.put("Registered By", row.get("Registered By"));
            processedRows.add(newRow);
        }
        return processedRows;
    }

    public List<Map<String, Object>> getDepartmentwiseRegistration(String fromDate, String toDate, String departmentId) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                p.created_at::DATE AS "Reg Date",
                sn.value AS "Patient No",
                p.salutation AS "salutation",
                p.first_name AS "first_name",
                p.last_name AS "last_name",
                CASE p.gender WHEN 0 THEN 'Male' WHEN 1 THEN 'Female' ELSE 'Other' END AS "Sex",
                CASE
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 year'
                        THEN EXTRACT(YEAR FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'y'
                    WHEN age(CURRENT_DATE, p.estimated_date_of_birth) >= interval '1 month'
                        THEN EXTRACT(MONTH FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'm'
                    ELSE
                        EXTRACT(DAY FROM age(CURRENT_DATE, p.estimated_date_of_birth))::text || 'd'
                END AS "Age",
                p.contact_number AS "contact_number",
                c.first_name AS "c_first_name",
                c.last_name AS "c_last_name",
                c.qualification AS "c_qualification",
                COALESCE(d.name, '') AS "Department",
                COALESCE(u.username, '') AS "Registered By"
            FROM patients p
            LEFT JOIN number_sequences sn ON p.id = sn.id
            LEFT JOIN consultants c ON p.primary_provider_id = c.id
            LEFT JOIN departments d ON c.department_id = d.id
            LEFT JOIN users u ON p.created_by = u.id
            WHERE p.created_at::DATE BETWEEN ?::DATE AND ?::DATE
              AND (? = '' OR d.id::text = ?)
            """);
        String depId = departmentId == null ? "" : departmentId;
        List<Object> args = new ArrayList<>(List.of(fromDate, toDate, depId, depId));
        sql.append(scope.predicate("p")); args.addAll(scope.args());
        sql.append(" ORDER BY \"Department\" ASC, p.created_at ASC");

        List<Map<String, Object>> rawRows = com.hms.application.report.util.ReportDbUtil.queryForList(jdbcTemplate, sql.toString(), args.toArray());
        List<Map<String, Object>> processedRows = new ArrayList<>();
        for (Map<String, Object> row : rawRows) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            newRow.put("Reg Date", row.get("Reg Date"));
            newRow.put("Patient No", row.get("Patient No"));

            // Decrypt Patient Name
            String salutation = (String) row.get("salutation");
            String firstName = decrypt((String) row.get("first_name"));
            String lastName = decrypt((String) row.get("last_name"));
            String patientName = "";
            if (salutation != null && !salutation.isBlank()) {
                patientName += salutation + " ";
            }
            if (firstName != null && !firstName.isBlank()) {
                patientName += firstName;
            }
            if (lastName != null && !lastName.isBlank()) {
                patientName += (patientName.isEmpty() ? "" : " ") + lastName;
            }
            newRow.put("Patient Name", patientName.trim());

            newRow.put("Sex", row.get("Sex"));
            newRow.put("Age", row.get("Age"));

            // Decrypt Contact Number
            newRow.put("Contact No", decrypt((String) row.get("contact_number")));

            // Decrypt Consultant Name
            String cFirstName = decrypt((String) row.get("c_first_name"));
            String cLastName = decrypt((String) row.get("c_last_name"));
            String cQual = (String) row.get("c_qualification");
            String consultant = "";
            if (cFirstName != null && !cFirstName.isBlank()) {
                consultant += cFirstName;
            }
            if (cLastName != null && !cLastName.isBlank()) {
                consultant += (consultant.isEmpty() ? "" : " ") + cLastName;
            }
            if (cQual != null && !cQual.isBlank()) {
                consultant += " (" + cQual + ")";
            }
            newRow.put("Consultant", consultant.trim());

            newRow.put("Department", row.get("Department"));
            newRow.put("Registered By", row.get("Registered By"));
            processedRows.add(newRow);
        }
        return processedRows;
    }
}
