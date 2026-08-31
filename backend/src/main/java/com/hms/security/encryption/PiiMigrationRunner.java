package com.hms.security.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * One-time migration utility that encrypts existing plaintext PII rows.
 *
 * HOW TO USE:
 *   1. Run Flyway migration V144 first (widens columns, adds pii_encrypted flag).
 *   2. Call migratePii() from an application startup runner, admin endpoint,
 *      or standalone CLI tool.
 *   3. The migration is idempotent — it only processes rows where pii_encrypted = FALSE.
 *   4. After 100% completion, drop the pii_encrypted column (V145 migration).
 *
 * EXAMPLE — run at startup once:
 * <pre>
 * {@literal @}Component
 * {@literal @}RequiredArgsConstructor
 * class PiiMigrationStartupRunner implements ApplicationRunner {
 *     private final PiiMigrationRunner runner;
 *     public void run(ApplicationArguments args) { runner.migratePii(); }
 * }
 * </pre>
 *
 * SAFETY: Uses JDBC directly (bypasses JPA converters so plaintext rows
 * can be read before encryption). Processes in batches of 200 to limit
 * transaction size and memory usage.
 */
@Component
public class PiiMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(PiiMigrationRunner.class);
    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final PiiEncryptionService enc;
    private final PiiSearchTokenService searchTokenService;

    public PiiMigrationRunner(JdbcTemplate jdbc, PiiEncryptionService enc, PiiSearchTokenService searchTokenService) {
        this.jdbc = jdbc;
        this.enc  = enc;
        this.searchTokenService = searchTokenService;
    }

    /** Migrates all tables. Safe to call multiple times. */
    @Transactional
    public void migratePii() {
        log.info("=== PII Encryption Migration Starting ===");
        resetIncorrectlyMarkedEncryptedRows();
        populateMissingEmailTokens();
        migratePatients();
        migrateUsers();
        migrateConsultants();
        migrateStaff();
        migrateCustomers();
        migrateReferrals();
        migratePayors();
        migrateSuppliers();
        migrateInsurances();
        migrateAppointments();
        migrateClinicalEncounters();
        // F-002. V208 added the converters and widened these columns; nothing
        // encrypted the rows that were already there. Until this runs, each of
        // these tables holds a mix of ciphertext and plaintext, and reads of the
        // older rows throw on decryption.
        migrateVisits();
        migrateNhcxTransactions();
        migratePharmacySales();
        // WO-029. pediatric_data and template_data were JSONB and unencrypted;
        // V214 converted the columns to TEXT and the converter tolerates both
        // forms, so this backfill can run without a flag day.
        migratePatientJsonColumns();
        log.info("=== PII Encryption Migration Complete ===");
    }

    private void resetIncorrectlyMarkedEncryptedRows() {
        log.info("Resetting incorrectly marked pii_encrypted flags...");

        jdbc.update("UPDATE patients SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(first_name IS NOT NULL AND (length(first_name) < 20 OR first_name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(last_name IS NOT NULL AND (length(last_name) < 20 OR last_name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(contact_number IS NOT NULL AND (length(contact_number) < 20 OR contact_number !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE users SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(first_name IS NOT NULL AND (length(first_name) < 20 OR first_name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(last_name IS NOT NULL AND (length(last_name) < 20 OR last_name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(phone_no IS NOT NULL AND (length(phone_no) < 20 OR phone_no !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE consultants SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(first_name IS NOT NULL AND (length(first_name) < 20 OR first_name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(last_name IS NOT NULL AND (length(last_name) < 20 OR last_name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(contact IS NOT NULL AND (length(contact) < 20 OR contact !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE staff SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(name IS NOT NULL AND (length(name) < 20 OR name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(contact IS NOT NULL AND (length(contact) < 20 OR contact !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE customers SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(name IS NOT NULL AND (length(name) < 20 OR name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(contact_no IS NOT NULL AND (length(contact_no) < 20 OR contact_no !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE referrals SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(name IS NOT NULL AND (length(name) < 20 OR name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(contact IS NOT NULL AND (length(contact) < 20 OR contact !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE payors SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(contact IS NOT NULL AND (length(contact) < 20 OR contact !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(contact_person IS NOT NULL AND (length(contact_person) < 20 OR contact_person !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE suppliers SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(contact IS NOT NULL AND (length(contact) < 20 OR contact !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(contact_person IS NOT NULL AND (length(contact_person) < 20 OR contact_person !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE insurances SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(policy_number IS NOT NULL AND (length(policy_number) < 20 OR policy_number !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(pre_auth_number IS NOT NULL AND (length(pre_auth_number) < 20 OR pre_auth_number !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE appointments SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(temp_patient_name IS NOT NULL AND (length(temp_patient_name) < 20 OR temp_patient_name !~ '^[A-Za-z0-9+/=]+$')) OR " +
                "(temp_patient_phone IS NOT NULL AND (length(temp_patient_phone) < 20 OR temp_patient_phone !~ '^[A-Za-z0-9+/=]+$')))");

        jdbc.update("UPDATE clinical_encounters SET pii_encrypted = FALSE WHERE pii_encrypted = TRUE AND (" +
                "(diagnosis IS NOT NULL AND (length(diagnosis) < 20 OR diagnosis !~ '^[A-Za-z0-9+/=]+$')))");
    }

    // ── patients ──────────────────────────────────────────────────────────────

    private void migratePatients() {
        String select = "SELECT id, first_name, last_name, contact_number, email, blood_group, address, contact_number_token " +
                        "FROM patients WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE patients SET first_name=?, last_name=?, contact_number=?, email=?, " +
                        "blood_group=?, address=?, contact_number_token=?, pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                String phoneToken = resolvePhoneToken(r, "contact_number", "contact_number_token");
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "first_name")),
                    encryptIfPlaintext(str(r, "last_name")),
                    encryptIfPlaintext(str(r, "contact_number")),
                    encryptIfPlaintext(str(r, "email")),
                    encryptIfPlaintext(str(r, "blood_group")),
                    encryptIfPlaintext(str(r, "address")),
                    phoneToken,
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("patients: encrypted {} rows", total);
    }

    // ── users ─────────────────────────────────────────────────────────────────

    private void migrateUsers() {
        String select = "SELECT id, first_name, last_name, email, email_token, phone_no, phone_no_token " +
                        "FROM users WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE users SET first_name=?, last_name=?, email=?, email_token=?, phone_no=?, " +
                        "phone_no_token=?, pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                String phoneToken = resolvePhoneToken(r, "phone_no", "phone_no_token");
                String emailToken = resolveEmailToken(r, "email", "email_token");
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "first_name")),
                    encryptIfPlaintext(str(r, "last_name")),
                    encryptIfPlaintext(str(r, "email")),
                    emailToken,
                    encryptIfPlaintext(str(r, "phone_no")),
                    phoneToken,
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("users: encrypted {} rows", total);
    }

    // ── consultants ───────────────────────────────────────────────────────────

    private void migrateConsultants() {
        String select = "SELECT id, first_name, last_name, contact, email, address, registration_no, contact_number_token " +
                        "FROM consultants WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE consultants SET first_name=?, last_name=?, contact=?, email=?, " +
                        "address=?, registration_no=?, contact_number_token=?, pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                String contactToken = resolvePhoneToken(r, "contact", "contact_number_token");
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "first_name")),
                    encryptIfPlaintext(str(r, "last_name")),
                    encryptIfPlaintext(str(r, "contact")),
                    encryptIfPlaintext(str(r, "email")),
                    encryptIfPlaintext(str(r, "address")),
                    encryptIfPlaintext(str(r, "registration_no")),
                    contactToken,
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("consultants: encrypted {} rows", total);
    }

    // ── staff ─────────────────────────────────────────────────────────────────

    private void migrateStaff() {
        String select = "SELECT id, name, contact, email, contact_token FROM staff " +
                        "WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE staff SET name=?, contact=?, email=?, contact_token=?, pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                String contactToken = resolvePhoneToken(r, "contact", "contact_token");
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "name")),
                    encryptIfPlaintext(str(r, "contact")),
                    encryptIfPlaintext(str(r, "email")),
                    contactToken,
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("staff: encrypted {} rows", total);
    }

    // ── customers ─────────────────────────────────────────────────────────────

    private void migrateCustomers() {
        String select = "SELECT id, name, address, contact_no, email FROM customers " +
                        "WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE customers SET name=?, address=?, contact_no=?, email=?, " +
                        "pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "name")),
                    encryptIfPlaintext(str(r, "address")),
                    encryptIfPlaintext(str(r, "contact_no")),
                    encryptIfPlaintext(str(r, "email")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("customers: encrypted {} rows", total);
    }

    // ── referrals ─────────────────────────────────────────────────────────────

    private void migrateReferrals() {
        String select = "SELECT id, name, contact, first_name, last_name, address FROM referrals " +
                        "WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE referrals SET name=?, contact=?, first_name=?, last_name=?, " +
                        "address=?, pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "name")),
                    encryptIfPlaintext(str(r, "contact")),
                    encryptIfPlaintext(str(r, "first_name")),
                    encryptIfPlaintext(str(r, "last_name")),
                    encryptIfPlaintext(str(r, "address")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("referrals: encrypted {} rows", total);
    }

    // ── payors ────────────────────────────────────────────────────────────────

    private void migratePayors() {
        String select = "SELECT id, contact, contact_person, email, address FROM payors " +
                        "WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE payors SET contact=?, contact_person=?, email=?, address=?, " +
                        "pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "contact")),
                    encryptIfPlaintext(str(r, "contact_person")),
                    encryptIfPlaintext(str(r, "email")),
                    encryptIfPlaintext(str(r, "address")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("payors: encrypted {} rows", total);
    }

    // ── suppliers ─────────────────────────────────────────────────────────────

    private void migrateSuppliers() {
        String select = "SELECT id, contact, contact_person, email, address, gstin FROM suppliers " +
                        "WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE suppliers SET contact=?, contact_person=?, email=?, address=?, " +
                        "gstin=?, pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "contact")),
                    encryptIfPlaintext(str(r, "contact_person")),
                    encryptIfPlaintext(str(r, "email")),
                    encryptIfPlaintext(str(r, "address")),
                    encryptIfPlaintext(str(r, "gstin")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("suppliers: encrypted {} rows", total);
    }

    // ── insurances ────────────────────────────────────────────────────────────

    private void migrateInsurances() {
        String select = "SELECT id, policy_number, pre_auth_number FROM insurances " +
                        "WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE insurances SET policy_number=?, pre_auth_number=?, " +
                        "pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "policy_number")),
                    encryptIfPlaintext(str(r, "pre_auth_number")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("insurances: encrypted {} rows", total);
    }

    // ── appointments ──────────────────────────────────────────────────────────

    private void migrateAppointments() {
        String select = "SELECT id, temp_patient_name, temp_patient_phone FROM appointments " +
                        "WHERE pii_encrypted = FALSE " +
                        "AND (temp_patient_name IS NOT NULL OR temp_patient_phone IS NOT NULL) " +
                        "LIMIT " + BATCH_SIZE;
        String update = "UPDATE appointments SET temp_patient_name=?, temp_patient_phone=?, " +
                        "pii_encrypted=TRUE WHERE id=?";
        // Mark registered-patient appointments (no temp fields) as done in bulk
        jdbc.update("UPDATE appointments SET pii_encrypted=TRUE " +
                    "WHERE pii_encrypted=FALSE AND temp_patient_name IS NULL AND temp_patient_phone IS NULL");
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "temp_patient_name")),
                    encryptIfPlaintext(str(r, "temp_patient_phone")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("appointments: encrypted {} temp-patient rows", total);
    }

    // ── clinical_encounters ───────────────────────────────────────────────────

    private void migrateClinicalEncounters() {
        String select = "SELECT id, diagnosis FROM clinical_encounters " +
                        "WHERE pii_encrypted = FALSE AND diagnosis IS NOT NULL LIMIT " + BATCH_SIZE;
        String update = "UPDATE clinical_encounters SET diagnosis=?, pii_encrypted=TRUE WHERE id=?";
        // Mark null-diagnosis encounters as done in bulk
        jdbc.update("UPDATE clinical_encounters SET pii_encrypted=TRUE " +
                    "WHERE pii_encrypted=FALSE AND diagnosis IS NULL");
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "diagnosis")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("clinical_encounters: encrypted {} diagnosis rows", total);
    }

    /**
     * F-002: {@code visits.diagnosis}.
     *
     * <p>This column was plaintext while {@code clinical_encounters.diagnosis} —
     * the same category of data, one table over — had been encrypted since the
     * original rollout. The kind of gap that only surfaces when someone
     * enumerates the schema rather than reading the code.
     */
    private void migrateVisits() {
        String select = "SELECT id, diagnosis FROM visits " +
                        "WHERE pii_encrypted = FALSE AND diagnosis IS NOT NULL LIMIT " + BATCH_SIZE;
        String update = "UPDATE visits SET diagnosis=?, pii_encrypted=TRUE WHERE id=?";
        jdbc.update("UPDATE visits SET pii_encrypted=TRUE " +
                    "WHERE pii_encrypted=FALSE AND diagnosis IS NULL");
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update, encryptIfPlaintext(str(r, "diagnosis")), r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("visits: encrypted {} diagnosis rows", total);
    }

    /**
     * F-002: {@code nhcx_transactions.diagnosis_code} and {@code .diagnosis_text}.
     *
     * <p>Both are health data, and identifying in combination with the
     * {@code patient_id} on the same row. Handled in one pass because they share
     * a flag — encrypting one and not the other would leave the row half done
     * with no way to tell which half.
     */
    private void migrateNhcxTransactions() {
        String select = "SELECT id, diagnosis_code, diagnosis_text FROM nhcx_transactions " +
                        "WHERE pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE nhcx_transactions SET diagnosis_code=?, diagnosis_text=?, " +
                        "pii_encrypted=TRUE WHERE id=?";
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "diagnosis_code")),
                    encryptIfPlaintext(str(r, "diagnosis_text")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("nhcx_transactions: encrypted {} diagnosis rows", total);
    }

    /**
     * F-002: {@code pharmacy_sales.customer_phone}.
     *
     * <p>A walk-in counter sale often has no patient record attached, so this
     * number is the only identifier on the row. No search token is generated:
     * unlike {@code patients.contact_number}, nothing queries this column by
     * value, so a plain converter is sufficient.
     */
    private void migratePharmacySales() {
        String select = "SELECT id, customer_phone FROM pharmacy_sales " +
                        "WHERE pii_encrypted = FALSE AND customer_phone IS NOT NULL LIMIT " + BATCH_SIZE;
        String update = "UPDATE pharmacy_sales SET customer_phone=?, pii_encrypted=TRUE WHERE id=?";
        jdbc.update("UPDATE pharmacy_sales SET pii_encrypted=TRUE " +
                    "WHERE pii_encrypted=FALSE AND customer_phone IS NULL");
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update, encryptIfPlaintext(str(r, "customer_phone")), r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        log.info("pharmacy_sales: encrypted {} customer_phone rows", total);
    }

    /**
     * WO-029: {@code patients.pediatric_data} and {@code patients.template_data}.
     *
     * <p>The values are JSON documents rather than scalars, so they are encrypted
     * as whole strings — the same treatment {@code EncryptedJsonMapConverter}
     * applies on write. No parsing happens here: a malformed document should be
     * preserved as-is and encrypted, not silently normalised by a migration.
     *
     * <p>Tracked by its own {@code json_pii_encrypted} flag rather than the
     * existing {@code pii_encrypted}, because that one is already TRUE for every
     * row from the original string-column migration and reusing it would skip
     * every patient.
     */
    private void migratePatientJsonColumns() {
        String select = "SELECT id, pediatric_data, template_data FROM patients " +
                        "WHERE json_pii_encrypted = FALSE LIMIT " + BATCH_SIZE;
        String update = "UPDATE patients SET pediatric_data=?, template_data=?, " +
                        "json_pii_encrypted=TRUE WHERE id=?";
        jdbc.update("UPDATE patients SET json_pii_encrypted=TRUE " +
                    "WHERE json_pii_encrypted=FALSE " +
                    "AND pediatric_data IS NULL AND template_data IS NULL");
        int total = 0;
        List<Map<String, Object>> rows;
        do {
            rows = jdbc.queryForList(select);
            for (Map<String, Object> r : rows) {
                jdbc.update(update,
                    encryptIfPlaintext(str(r, "pediatric_data")),
                    encryptIfPlaintext(str(r, "template_data")),
                    r.get("id"));
                total++;
            }
        } while (rows.size() == BATCH_SIZE);
        // Row count only. The documents themselves are the thing being protected.
        log.info("patients: encrypted {} json column rows", total);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }

    private String encryptIfPlaintext(String value) {
        if (value == null) return null;
        if (enc.looksEncrypted(value)) return value;
        return enc.encrypt(value);
    }

    private String resolvePhoneToken(Map<String, Object> r, String phoneCol, String tokenCol) {
        String rawPhone = str(r, phoneCol);
        if (rawPhone == null) return null;
        if (enc.looksEncrypted(rawPhone)) {
            String existingToken = str(r, tokenCol);
            if (existingToken != null) {
                return existingToken;
            }
            try {
                String decrypted = enc.decrypt(rawPhone);
                return searchTokenService.phoneToken(decrypted);
            } catch (Exception e) {
                // WO-028: a decryption failure message can quote the ciphertext or a
                    // partial plaintext. The row id is enough to investigate.
                    log.warn("Failed to decrypt already-encrypted phone for token generation on row {}: {}", r.get("id"), e.getClass().getSimpleName());
                return null;
            }
        }
        return searchTokenService.phoneToken(rawPhone);
    }

    private String resolveEmailToken(Map<String, Object> r, String emailCol, String tokenCol) {
        String rawEmail = str(r, emailCol);
        if (rawEmail == null) return null;
        if (enc.looksEncrypted(rawEmail)) {
            String existingToken = str(r, tokenCol);
            if (existingToken != null) {
                return existingToken;
            }
            try {
                String decrypted = enc.decrypt(rawEmail);
                return searchTokenService.token(decrypted);
            } catch (Exception e) {
                log.warn("Failed to decrypt already-encrypted email for token generation on row {}: {}", r.get("id"), e.getClass().getSimpleName());
                return null;
            }
        }
        return searchTokenService.token(rawEmail);
    }

    private void populateMissingEmailTokens() {
        log.info("Populating missing email tokens for already-encrypted users...");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, email FROM users WHERE email IS NOT NULL AND email_token IS NULL"
        );
        int count = 0;
        for (Map<String, Object> r : rows) {
            String encryptedEmail = str(r, "email");
            if (encryptedEmail != null && !encryptedEmail.isBlank()) {
                try {
                    String decryptedEmail = enc.looksEncrypted(encryptedEmail) ? enc.decrypt(encryptedEmail) : encryptedEmail;
                    String token = searchTokenService.token(decryptedEmail);
                    jdbc.update("UPDATE users SET email_token = ? WHERE id = ?", token, r.get("id"));
                    count++;
                } catch (Exception e) {
                    log.error("Failed to populate email token for user {}: {}", r.get("id"), e.getClass().getSimpleName());
                }
            }
        }
        if (count > 0) {
            log.info("Populated email tokens for {} users.", count);
        }
    }
}
