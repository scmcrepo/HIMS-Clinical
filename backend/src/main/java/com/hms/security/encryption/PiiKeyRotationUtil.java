package com.hms.security.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Utility for rotating the PII encryption key.
 *
 * When to rotate:
 *   - Staff member with key access leaves the organisation
 *   - Suspected key compromise
 *   - Regular rotation policy (e.g. annually)
 *
 * How to rotate:
 *   1. Generate a new 256-bit key:  openssl rand -base64 32
 *   2. Add the NEW key to config:   hms.security.encryption.key.new=<new_key>
 *   3. Call rotateAllKeys(oldKey, newKey) via an admin endpoint or CLI.
 *   4. Once complete, remove the old key and rename .new → .key in config.
 *   5. Restart the application.
 *
 * This is a READ-OLD / WRITE-NEW operation — atomic per row within a transaction.
 */
@Component
public class PiiKeyRotationUtil {

    private static final Logger log = LoggerFactory.getLogger(PiiKeyRotationUtil.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;

    public PiiKeyRotationUtil(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Rotates encryption key across all PII tables.
     *
     * @param oldBase64Key current key (Base64-encoded 32 bytes)
     * @param newBase64Key new key    (Base64-encoded 32 bytes)
     */
    public void rotateAllKeys(String oldBase64Key, String newBase64Key) {
        PiiEncryptionService oldEnc = buildService(oldBase64Key);
        PiiEncryptionService newEnc = buildService(newBase64Key);

        log.info("=== PII Key Rotation Starting ===");
        rotateTable("patients",
            "SELECT id, first_name, last_name, contact_number, email, blood_group, address FROM patients LIMIT " + BATCH_SIZE,
            oldEnc, newEnc,
            row -> jdbc.update(
                "UPDATE patients SET first_name=?, last_name=?, contact_number=?, email=?, blood_group=?, address=? WHERE id=?",
                newEnc.encrypt(oldEnc.decrypt(str(row, "first_name"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "last_name"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "contact_number"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "email"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "blood_group"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "address"))),
                row.get("id")));

        rotateTable("users",
            "SELECT id, first_name, last_name, email, phone_no FROM users LIMIT " + BATCH_SIZE,
            oldEnc, newEnc,
            row -> jdbc.update(
                "UPDATE users SET first_name=?, last_name=?, email=?, phone_no=? WHERE id=?",
                newEnc.encrypt(oldEnc.decrypt(str(row, "first_name"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "last_name"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "email"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "phone_no"))),
                row.get("id")));

        rotateTable("consultants",
            "SELECT id, first_name, last_name, contact, email, address, registration_no FROM consultants LIMIT " + BATCH_SIZE,
            oldEnc, newEnc,
            row -> jdbc.update(
                "UPDATE consultants SET first_name=?, last_name=?, contact=?, email=?, address=?, registration_no=? WHERE id=?",
                newEnc.encrypt(oldEnc.decrypt(str(row, "first_name"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "last_name"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "contact"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "email"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "address"))),
                newEnc.encrypt(oldEnc.decrypt(str(row, "registration_no"))),
                row.get("id")));

        log.info("=== PII Key Rotation Complete ===");
    }

    private void rotateTable(String table, String selectSql,
                             PiiEncryptionService oldEnc, PiiEncryptionService newEnc,
                             java.util.function.Consumer<Map<String, Object>> updater) {
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(selectSql);
            for (Map<String, Object> row : rows) {
                updater.accept(row);
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("{}: rotated {} rows", table, total);
    }

    private PiiEncryptionService buildService(String base64Key) {
        return new PiiEncryptionService(base64Key);
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }
}
